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
 * This service manages a local db with the list of all veichle identification numbers (vin) declared on the chain.
 * The vin could be present inside two type of boxes: CarBox and CarSellOrderBox.
 */
public class IDInfoDBService {

    private Storage IDInfoStorage;
    protected Logger log = LoggerFactory.getLogger(IDInfoDBService.class.getName());

    @Inject
    public IDInfoDBService(@Named("CarInfoStorage") Storage IDInfoStorage){
        this.IDInfoStorage = IDInfoStorage;
        log.debug("TokenInfoStorage now contains: "+ IDInfoStorage.getAll().size()+" elements");
    }

    public void updateAll(byte[] version, Set<String> idToAdd, HashMap<String, Integer> typeToAdd){
        log.debug("TokenInfoStorage updateID");
        List<Pair<ByteArrayWrapper, ByteArrayWrapper>> toUpdate = new ArrayList<>(idToAdd.size()+typeToAdd.size());
        idToAdd.forEach(ele -> {
            toUpdate.add(buildDBElement(ele));
        });

        log.debug("TokenInfoStorage updateTypeCount");
        typeToAdd.keySet().forEach(ele -> {
            int count = getTypeCount(ele);
            toUpdate.add(buildDBCountingElement(ele,typeToAdd.get(ele)+count));
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
                Set<String> vinInMempool = extractIdFromBoxes(transaction.newBoxes());
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
     * Extracts the list of vehicle identification numbers (vin) declared in the given box list.
     * The vin could be present inside two type of boxes: CarBox and CarSellOrderBox
     */
    public Set<String> extractIdFromBoxes(List<Box<Proposition>> boxes){
        Set<String> idList = new HashSet<String>();
        for (Box<Proposition> currentBox : boxes) {
            if (TokenBox.class.isAssignableFrom(currentBox.getClass())){
                String id  = TokenBox.parseBytes(currentBox.bytes()).getID();
                idList.add(id);
            } else if (TokenSellOrderBox.class.isAssignableFrom(currentBox.getClass())){
                String id  = TokenSellOrderBox.parseBytes(currentBox.bytes()).getID();
                idList.add(id);
            }
        }
        return idList;
    }

    public HashMap<String, Integer> extractTypeFromBoxes(List<Box<Proposition>> boxes){
        HashMap<String, Integer> typeList = new HashMap<String, Integer>();
        for (Box<Proposition> currentBox : boxes) {
            if (TokenBox.class.isAssignableFrom(currentBox.getClass())){
                String type  = TokenBox.parseBytes(currentBox.bytes()).getType();
                if (typeList.containsKey(type)) {
                    typeList.put(type, typeList.get(type)+1);
                }
                else {
                    typeList.put(type,1);
                }
            } else if (TokenSellOrderBox.class.isAssignableFrom(currentBox.getClass())){
                String type  = TokenSellOrderBox.parseBytes(currentBox.bytes()).getID();
                if (typeList.containsKey(type)) {
                    typeList.put(type, typeList.get(type)+1);
                }
                else {
                    typeList.put(type,1);
                }
            }
        }
        return typeList;
    }

    public String extractIdFromBox(Box<Proposition> box) {
        String id = "";
        if (TokenBox.class.isAssignableFrom(box.getClass())){
            id += TokenBox.parseBytes(box.bytes()).getID();
        } else if (TokenSellOrderBox.class.isAssignableFrom(box.getClass())){
            id += TokenSellOrderBox.parseBytes(box.bytes()).getID();
        }
        return id;
    }

    public String extractTypeFromBox(Box<Proposition> box){
        String type = "";
        if (TokenBox.class.isAssignableFrom(box.getClass())){
            type += TokenBox.parseBytes(box.bytes()).getType();
        } else if (TokenSellOrderBox.class.isAssignableFrom(box.getClass())){
            type += TokenSellOrderBox.parseBytes(box.bytes()).getID();
        }

        return type;
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
