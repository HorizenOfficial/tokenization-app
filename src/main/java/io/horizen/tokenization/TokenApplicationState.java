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
import io.horizen.tokenization.token.config.TokenDictionary;
import io.horizen.tokenization.token.config.TokenDictionaryItem;
import io.horizen.tokenization.token.services.IDInfoDBService;
import io.horizen.tokenization.token.transaction.CreateTokensTransaction;
import io.horizen.tokenization.token.transaction.ForgeTokensTransaction;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Success;
import scala.util.Try;
import java.util.*;
import scala.collection.JavaConverters;


public class TokenApplicationState implements ApplicationState {

	private IDInfoDBService IDInfoDbService;
    private TokenDictionary tokenDictionary;

    private static Logger log =  LoggerFactory.getLogger(TokenApplicationState.class);

	@Inject
	public TokenApplicationState(
	        IDInfoDBService IDInfoDbService,
            @Named("TokenDictionary") TokenDictionary tokenDictionary) {
	    this.IDInfoDbService = IDInfoDbService;
        this.tokenDictionary = tokenDictionary;
    }

    /**
     * Block validation.
     * We checks the following constraints:
     * - a block can contain only zero or ONE ForgeTokensTransaction
     * - the tokens produced by the ForgeTokensTransaction must be locked by the block forger public key
     * - different transactions in the same block (both forgetoken or manual token creation) can't declare the same tokenID
     * - overall number of token created inside the block (both by forgetoken or manual token creation)  must not exceed the max number of tokens
     * Further checks will be performed at single transaction level (see below)
     */
    @Override
    public boolean validate(SidechainStateReader stateReader, SidechainBlock block) {
        HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
        Set<String> tokenIdList = new HashSet<>();
        int numberOfForgerTransactions = 0;
        for (BoxTransaction<Proposition, Box<Proposition>> t :  JavaConverters.seqAsJavaList(block.transactions())){
            if (ForgeTokensTransaction.class.isInstance(t)){
                numberOfForgerTransactions++;
                if (numberOfForgerTransactions>1){
                    log.warn("Error during block validation: a block can contain only maximum one ForgeTokensTransaction");
                    return false;
                }
                for (Box box : t.newBoxes()) {
                    if (!Arrays.equals(box.proposition().bytes(), block.forgerPublicKey().pubKeyBytes())) {
                        log.warn("Error during block validation: ForgeTokensTransaction can only produce TokenBox to the block forger");
                        return false;
                    }
                }
            }
            if (ForgeTokensTransaction.class.isInstance(t) || CreateTokensTransaction.class.isInstance(t)){
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
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.tokenDictionary.getItem(key).getMaxSupply()) {
                    log.warn("Error during block validation: Exceeded the maximum number of tokens that can be created inside the block");
                    return false;
                }
            }
        }
        return true;
	}

    /**
     * Single transaction validation.
     * For ForgeTokensTransaction we check the following constraints:
     * - does not have any input box
     * - can only produce TokenBox in output
     * - cannot produce more token than the one defined in conf file
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
        if (ForgeTokensTransaction.class.isInstance(transaction)){
            if (transaction.boxIdsToOpen().size() > 0){
                log.warn("Error during transaction validation: ForgeTokensTransaction cannot have input box");
                return false;
            }
            HashMap<String, Integer> forgedCounterPerType = new HashMap<String, Integer>();
            for (Box box : transaction.newBoxes()) {
                if (!TokenBox.class.isInstance(box)) {
                    log.warn("Error during transaction validation: ForgeTokensTransaction can only produce TokenBox");
                    return false;
                }
                TokenBox currBox = TokenBox.parseBytes(box.bytes());
                TokenDictionaryItem config = this.tokenDictionary.getItem(currBox.getType());
                if (config == null){
                    log.warn("Error during transaction validation: unknown token type!");
                    return false;
                }else{
                    String type = currBox.getType();
                    if (forgedCounterPerType.containsKey(type)) {
                        forgedCounterPerType.put(type, forgedCounterPerType.get(type)+1);
                    }
                    else {
                        forgedCounterPerType.put(type, 1);
                    }
                }
            }
            for (String key : forgedCounterPerType.keySet()) {
                if(forgedCounterPerType.get(key) > this.tokenDictionary.getItem(key).getCreationPerBlock()) {
                    log.warn("Error during transaction validation: exceeded the maximum number of tokens that can be automatically forged inside a single block!");
                    return false;
                }
            }


        }else if (CreateTokensTransaction.class.isInstance(transaction)){
            CreateTokensTransaction txCt = CreateTokensTransaction.parseBytes(transaction.bytes());
            HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
            List<String> creatorPropositions = new ArrayList<>();
            String type = null;
            for (Box box : transaction.newBoxes()) {
                if (TokenBox.class.isInstance(box)) {
                    TokenBox currBox = TokenBox.parseBytes(box.bytes());

                    TokenDictionaryItem config = this.tokenDictionary.getItem(currBox.getType());
                    // Check that the token type is included in the config
                    if (config == null){
                        log.warn("Error during transaction validation: unknown token type!");
                        return false;
                    }
                    creatorPropositions = config.getCreatorPropositions();
                    // Check that the created tokens can be unlocked by one of the creators listed in the config file
                    if (!creatorPropositions.contains(ByteUtils.toHexString(box.proposition().bytes()))) {
                        log.warn("Error during transaction validation: token emitted to unallowed public key");
                        return false;
                    }

                    // Check that token ID is not already used
                    if (! IDInfoDbService.validateId(currBox.getTokenId(), Optional.empty())){
                        log.warn("Error during transaction validation: The token ID already exists!");
                        return false;
                    }
                    type = currBox.getType();
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
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.tokenDictionary.getItem(type).getMaxSupply()) {
                    log.warn("Error during transaction validation: Exceed the maximum number of tokens that can be created inside the transaction!");
                    return false;
                }
            }

            //checks that the creator of the transaction is one of the ones identified by the public keys in the
            //config file: in order to do that we verify the creator signature against one of the public keys listed in conf
            boolean authorized = false;
            for (String publicKey : creatorPropositions) {
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
        for (String tokenType: this.tokenDictionary.getAllTypes()){
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
