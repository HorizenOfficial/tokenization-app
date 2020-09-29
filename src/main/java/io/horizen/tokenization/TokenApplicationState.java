package io.horizen.tokenization;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.horizen.block.SidechainBlock;
import com.horizen.box.Box;
import com.horizen.box.BoxUnlocker;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.state.ApplicationState;
import com.horizen.state.SidechainStateReader;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.box.TokenBox;
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
    private ArrayList<String> creator;
    private HashMap<String, Integer> maxTokenPerType;

    private static Logger log =  LoggerFactory.getLogger(TokenApplicationState.class);

	@Inject
	public TokenApplicationState(IDInfoDBService IDInfoDbService, @Named("ConfigTokenizationApp") Config config) {
	    this.IDInfoDbService = IDInfoDbService;
	    this.config = config;
        this.creator = (ArrayList<String>) this.config.getObject("token").get("creatorPropositions").unwrapped();
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
                        if (tokenIdList.contains(currBox.getID())){
                            log.warn("Error during block validation: same token ID declared inside the block");
                            return false;
                        }else{
                            tokenIdList.add(currBox.getID());
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
     * We check the following constraints:
     * - the create token transaction must be performed by one of the token creators contained in the config file
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
                    // Check that only the propositions specified in the config are able to create tokens
                    if (!this.creator.contains(ByteUtils.toHexString(box.proposition().bytes()))) {
                        log.warn("Error during transaction validation: this proposition is not allowed to create tokens!");
                        return false;
                    }
                    // Check that the token type is included in the config
                    if (!maxTokenPerType.containsKey(currBox.getType())){
                        log.warn("Error during transaction validation: unknown token type!");
                        return false;
                    }
                    // Check that token ID is not already used
                    if (! IDInfoDbService.validateId(IDInfoDbService.extractIdFromBox(box), Optional.empty())){
                        log.warn("Error during transaction validation: The token ID already exists!");
                        return false;
                    }
                    String type = IDInfoDbService.extractTypeFromBox(box);
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
                    System.out.println("Error during transaction validation: Exceed the maximum number of tokens that can be created inside the transaction!");
                    return false;
                }
            }

            //checks that the creator of the transaction is one of the ones identified by the public keys in the
            //config file: in order to do that we verify the creator signature against one of the public keys
            boolean authorized = false;
            for (String publicKey : this.creator) {
                PublicKey25519Proposition testProposition = PublicKey25519PropositionSerializer.getSerializer()
                        .parseBytes(BytesUtils.fromHexString(publicKey));
                if (testProposition.verify(transaction.messageToSign(), txCt.getCreatorSignature())) {
                    authorized = true;
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
        //We update the tokein id info database. The data from it will be used during validation.
        //collect the id to be added and the the count for each toketype
        Set<String> idToAdd = IDInfoDbService.extractIdFromBoxes(newBoxes);
        HashMap<String,Integer> typeToAdd = IDInfoDbService.extractTypeFromBoxes(newBoxes);
        IDInfoDbService.updateAll(version, idToAdd,typeToAdd);
        return new Success<>(this);
    }


    @Override
    public Try<ApplicationState> onRollback(byte[] version) {
        IDInfoDbService.rollback(version);
        return new Success<>(this);
    }
}
