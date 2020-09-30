package io.horizen.tokenization.token.services;

import cats.kernel.Hash;
import com.google.common.primitives.Ints;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.horizen.box.Box;
import com.horizen.node.NodeMemoryPool;
import com.horizen.proposition.Proposition;
import com.horizen.storage.Storage;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.Pair;
import com.typesafe.config.ConfigValue;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scorex.crypto.hash.Blake2b256;
import java.util.*;
import com.typesafe.config.Config;

/**
 * This service manages a local db with the list of all token IDS declared on the chain.
 * The tokenId could be present inside two type of boxes: TokenBox and TokenSellOrderBox.
 * For each type the service stores also a counter of all tokens forged of that type.
 */
public class IDInfoDBService {

    private Storage IDInfoStorage;
    protected Logger log = LoggerFactory.getLogger(IDInfoDBService.class.getName());

    @Inject
    public IDInfoDBService(@Named("TokenInfoStorage") Storage IDInfoStorage){
        this.IDInfoStorage = IDInfoStorage;
        log.debug("TokenInfoStorage now contains: "+ IDInfoStorage.getAll().size()+" elements");
    }

    public void updateAll(byte[] version, Set<String> idToAdd, Map<String, Integer> forgedCounters){
        log.debug("TokenInfoStorage updateID");
        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = new ArrayList<>(idToAdd.size()+forgedCounters.size());
        idToAdd.forEach(ele -> {
            toUpdate.add(buildDBElement(ele));
        });

        log.debug("TokenInfoStorage updateTypeCount");
        forgedCounters.keySet().forEach(ele -> {
            int count = getTypeCount(ele);
            toUpdate.add(buildDBCountingElement(ele,forgedCounters.get(ele)+count));
        });
        IDInfoStorage.update(new ByteArrayWrapper(version), toUpdate, new ArrayList<>());

        log.debug("TokenInfoStorage now contains: "+ IDInfoStorage.getAll().size()+" elements");
    }

    public int getTypeCount(String key) {
        if (IDInfoStorage.get(buildDBCountingElement(key,0).getKey()).isPresent()) {
            int count = Ints.fromByteArray(IDInfoStorage.get(buildDBCountingElement(key,0).getKey()).get().data());
            return count;
        }
        return 0;
    }

    /**
     * Validate the given vehicle identification number against the db list and (optionally) the mempool transactions.
     * @param id the vehicle identification number to check
     * @param memoryPool if not null, the vin is checked also against the mempool transactions
     * @return true if the vin is valid (not already declared)
     */
    public boolean validateId(String id, Optional<NodeMemoryPool> memoryPool){
        if (IDInfoStorage.get(buildDBElement(id).getKey()).isPresent()){
            return false;
        }
        //in the vin is not found, and the mempool was provided, we check also there
        if (memoryPool.isPresent()) {
            for (BoxTransaction<Proposition, Box<Proposition>> transaction : memoryPool.get().getTransactions()) {
                Set<String> vinInMempool = extractTokenIdFromBoxes(transaction.newBoxes());
                if (vinInMempool.contains(id)){
                    return false;
                }
            }
        }
        //if we arrive here, the vin is valid
        return true;
    }

    public void rollback(byte[] version) {
        IDInfoStorage.rollback(new ByteArrayWrapper(version));
    }

    /**
     * Extracts the list of tokenId declared in the given box list.
     * The tokeInd could be present inside two type of boxes: TokenBox and TokenSellOrderBox
     */
    public Set<String> extractTokenIdFromBoxes(List<Box<Proposition>> boxes){
        Set<String> idList = new HashSet<String>();
        for (Box<Proposition> currentBox : boxes) {
            if (TokenBox.class.isAssignableFrom(currentBox.getClass())){
                String id  = TokenBox.parseBytes(currentBox.bytes()).getTokenId();
                idList.add(id);
            } else if (TokenSellOrderBox.class.isAssignableFrom(currentBox.getClass())){
                TokenSellOrderBox sellOrderBox = TokenSellOrderBox.parseBytes(currentBox.bytes());
                for (int i = 0; i < sellOrderBox.getBoxData().getOrderItemLenght(); i++){
                    String id  =  sellOrderBox.getBoxData().getOrderItem(i).getTokenId();
                    idList.add(id);
                }
            }
        }
        return idList;
    }


    private Pair<ByteArrayWrapper, ByteArrayWrapper> buildDBElement(String id){
        //we hash the vin to be sure the key has a  fixed size of 32, which is the default of iohk.iodb used as underline storage
        ByteArrayWrapper keyWrapper = new ByteArrayWrapper(Blake2b256.hash(id));
        //the value is not important (we need just a key set, each key is a vin hash)
        ByteArrayWrapper valueWrapper = new ByteArrayWrapper(new byte[1]);
        return new Pair<>(keyWrapper, valueWrapper);
    }

    private Pair<ByteArrayWrapper, ByteArrayWrapper> buildDBCountingElement(String id, int value){
        //we hash the vin to be sure the key has a  fixed size of 32, which is the default of iohk.iodb used as underline storage
        ByteArrayWrapper keyWrapper = new ByteArrayWrapper(Blake2b256.hash(id));
        //the value is not important (we need just a key set, each key is a vin hash)
        ByteArrayWrapper valueWrapper = new ByteArrayWrapper(Ints.toByteArray(value));
        return new Pair<>(keyWrapper, valueWrapper);
    }
}
