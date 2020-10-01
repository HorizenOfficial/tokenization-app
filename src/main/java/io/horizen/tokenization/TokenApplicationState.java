package io.horizen.tokenization;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.state.ApplicationState;
import com.horizen.state.SidechainStateReader;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import io.horizen.tokenization.token.services.IDInfoDBService;
import io.horizen.tokenization.token.transaction.CreateTokensTransaction;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Success;
import scala.util.Try;

import java.util.*;

import scala.collection.JavaConverters;
import com.typesafe.config.Config;

public class TokenApplicationState implements ApplicationState {

	private IDInfoDBService IDInfoDbService;
	private Config config;
    private ArrayList<String> creatorPropositions;
    private HashMap<String, Integer> maxTokenPerType;

    private static Logger log =  LoggerFactory.getLogger(TokenApplicationState.class);

	@Inject
	public TokenApplicationState(IDInfoDBService IDInfoDbService, @Named("ConfigTokenizationApp") Config config) {
	    this.IDInfoDbService = IDInfoDbService;
	    this.config = config;
        this.creatorPropositions = (ArrayList<String>) this.config.getObject("token").get("creatorPropositions").unwrapped();
        this.maxTokenPerType = (HashMap<String,Integer>) this.config.getObject("token").get("typeLimit").unwrapped();
    }

    /**
     * Block validation.
     * We checks the following constraints:
     * - different transactions in the same block can't declare the same tokenID
     * - overall number of token created inside the block must not exceed the max number of tokens
     * Further checks will be performed at single transaction level (see below)
     */
    @Override
    public boolean validate(SidechainStateReader stateReader, SidechainBlock block) {
        HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
        Set<String> tokenIdList = new HashSet<>();
        for (BoxTransaction<Proposition, Box<Proposition>> t :  JavaConverters.seqAsJavaList(block.transactions())){
            if (CreateTokensTransaction.class.isInstance(t)){
                for (Box box : t.newBoxes()) {
                    if (TokenBox.class.isInstance(box)) {
                        TokenBox currBox = TokenBox.parseBytes(box.bytes());
                        if (tokenIdList.contains(currBox.getTokenId())){
                            log.warn("Error during block validation: same token ID declared inside the block");
                            return false;
                        }else{
                            tokenIdList.add(currBox.getTokenId());
                        }
                        String type = currBox.getType();
                        if (typeCount.containsKey(type)) {
                            typeCount.put(type, typeCount.get(type)+1);                        }
                        else {
                            typeCount.put(type, 1);
                        }
                    }
                }
            }
            // Check that the max limit of token is not reached
            for (String key : typeCount.keySet()) {
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.maxTokenPerType.get(key)) {
                    log.warn("Error during block validation: Exceeded the maximum number of tokens that can be created inside the block");
                    return false;
                }
            }
        }
        return true;
	}

    /**
     * Single transaction validation.
     * For CreateTokensTransaction we check the following constraints:
     * - the creator of the transaction demonstrates (by a signature) that he owned a the private keys corresponding to  one of the public keys identifying
     *   the token creators contained in the config file
     * - the created tokens can be unlocked by one of the creators listed in the config file
     * - the token type must be contained in the config file
     * - the token id must not be already present
     * - the max limit of token for each type is not reached
     */
    @Override
    public boolean validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction) {
        if (CreateTokensTransaction.class.isInstance(transaction)){
            CreateTokensTransaction txCt = CreateTokensTransaction.parseBytes(transaction.bytes());
            HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
            for (Box box : transaction.newBoxes()) {
                if (TokenBox.class.isInstance(box)) {
                    TokenBox currBox = TokenBox.parseBytes(box.bytes());
                    // Check that the created tokens can be unlocked by one of the creators listed in the config file
                    if (!this.creatorPropositions.contains(ByteUtils.toHexString(box.proposition().bytes()))) {
                        log.warn("Error during transaction validation: token emitted to unallowed public key");
                        return false;
                    }
                    // Check that the token type is included in the config
                    if (!maxTokenPerType.containsKey(currBox.getType())){
                        log.warn("Error during transaction validation: unknown token type!");
                        return false;
                    }
                    // Check that token ID is not already used
                    if (! IDInfoDbService.validateId(currBox.getTokenId(), Optional.empty())){
                        log.warn("Error during transaction validation: The token ID already exists!");
                        return false;
                    }
                    String type = currBox.getType();
                    if (typeCount.containsKey(type)) {
                        typeCount.put(type, typeCount.get(type)+1);
                    }
                    else {
                        typeCount.put(type, 1);
                    }
                }
            }
            // Check that the max limit of token is not reached
            for (String key : typeCount.keySet()) {
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.maxTokenPerType.get(key)) {
                    log.warn("Error during transaction validation: Exceed the maximum number of tokens that can be created inside the transaction!");
                    return false;
                }
            }

            //checks that the creator of the transaction is one of the ones identified by the public keys in the
            //config file: in order to do that we verify the creator signature against one of the public keys listed in conf
            boolean authorized = false;
            for (String publicKey : this.creatorPropositions) {
                PublicKey25519Proposition testProposition = PublicKey25519PropositionSerializer.getSerializer()
                        .parseBytes(BytesUtils.fromHexString(publicKey));
                if (testProposition.verify(transaction.messageToSign(), txCt.getCreatorSignature())) {
                    authorized = true;
                    break;
                }
            }
            if(!authorized) {
                log.warn("Error during transaction validation: creator not authorized");
                return false;
            }

        }
        return true;
    }

    @Override
    public Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader,
                                                byte[] version,
                                                List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove) {
        //We update the tokein id info database (the data from it will be used during validation) and
        //the counter of new forged tokens for each type (the data from it will be used for the tokenApi/supply endpoint)

        //this list will collect all the new tokenId in these newboxes
        Set<String> idList = new HashSet<String>();

        //this list will mantain the counter of the number of new token forged for each type in these newboxes
        //(counter initially set to 0)
        Map<String, Integer> forgedCounters = new HashMap();
        for (String tokenType: this.maxTokenPerType.keySet()){
            forgedCounters.put(tokenType, 0);
        }

        for (Box<Proposition> currentBox : newBoxes) {
            if (TokenBox.class.isAssignableFrom(currentBox.getClass())){
                TokenBox currTBox = TokenBox.parseBytes(currentBox.bytes());
                String id  = currTBox.getTokenId();
                String type = currTBox.getType();
                if ((IDInfoDbService.validateId(id, Optional.empty()))){
                    //if the id is validated, it was not present on the DB, so the token has been just forged: we increase the forgedCount
                    forgedCounters.put(type, forgedCounters.get(type) + 1);
                }
                idList.add(id);
            } else if (TokenSellOrderBox.class.isAssignableFrom(currentBox.getClass())){
                TokenSellOrderBox sellOrderBox = TokenSellOrderBox.parseBytes(currentBox.bytes());
                for (int i = 0; i < sellOrderBox.getBoxData().getOrderItemLenght(); i++){
                    String id  =  sellOrderBox.getBoxData().getOrderItem(i).getTokenId();
                    idList.add(id);
                }
            }
        }
        IDInfoDbService.updateAll(version, idList, forgedCounters);
        return new Success<>(this);
    }


    @Override
    public Try<ApplicationState> onRollback(byte[] version) {
        IDInfoDbService.rollback(version);
        return new Success<>(this);
    }
}
