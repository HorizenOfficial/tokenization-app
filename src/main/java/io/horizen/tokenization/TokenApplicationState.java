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

	@Inject
	public TokenApplicationState(IDInfoDBService IDInfoDbService, @Named("ConfigTokenizationApp") Config config) {
	    this.IDInfoDbService = IDInfoDbService;
	    this.config = config;
        this.creator = (ArrayList<String>) this.config.getObject("token").get("creatorPropositions").unwrapped();
        this.maxTokenPerType = (HashMap<String,Integer>) this.config.getObject("token").get("typeLimit").unwrapped();
    }

    @Override
    public boolean validate(SidechainStateReader stateReader, SidechainBlock block) {
        //We check that there are no multiple transactions declaring the same VIN inside the block
        HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
        for (BoxTransaction<Proposition, Box<Proposition>> t :  JavaConverters.seqAsJavaList(block.transactions())){
            if (CreateTokensTransaction.class.isInstance(t)){
                for (Box box : t.newBoxes()) {
                    if (TokenBox.class.isInstance(box)) {
                        // Check that only the propositions specified in the config are able to create tokens
                        if (!this.creator.contains(ByteUtils.toHexString(box.proposition().bytes()))) {
                            System.out.println("Error during block validation: this proposition is not allowed to create tokens!");
                            return false;
                        }
                        // Check that token ID is not already used
                        if (! IDInfoDbService.validateId(IDInfoDbService.extractIdFromBox(box), Optional.empty())){
                            System.out.println("Error during block validation: The token ID already exists!");
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
                boolean authorized = false;
                for (BoxUnlocker<Proposition> input : t.unlockers()) {
                    for (String publicKey : this.creator) {
                        System.out.println("PublicKey "+publicKey);
                        PublicKey25519Proposition testProposition = PublicKey25519PropositionSerializer.getSerializer()
                                .parseBytes(BytesUtils.fromHexString(publicKey));
                        if (testProposition.verify(t.messageToSign(),input.boxKey().bytes())) {
                            System.out.println("Authorized for: "+publicKey);
                            authorized = true;
                        }
                    }
                }
                if(!authorized) {
                    System.out.println("Not authorized!");
                    return false;
                }
            }
            // Check that the max limit of token is not reached
            for (String key : typeCount.keySet()) {
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.maxTokenPerType.get(key)) {
                    System.out.println("Error: Exceed the maximum number of tokens that can be created inside the block!");
                    return false;
                }
            }
        }
        return true;
	}

    @Override
    public boolean validate(SidechainStateReader stateReader, BoxTransaction<Proposition, Box<Proposition>> transaction) {
        if (CreateTokensTransaction.class.isInstance(transaction)){
            HashMap<String, Integer> typeCount = new HashMap<String, Integer>();
            for (Box box : transaction.newBoxes()) {
                if (TokenBox.class.isInstance(box)) {
                    // Check that only the propositions specified in the config are able to create tokens
                    if (!this.creator.contains(ByteUtils.toHexString(box.proposition().bytes()))) {
                        System.out.println("Error during transaction validation: this proposition is not allowed to create tokens!");
                        return false;
                    }

                    // Check that token ID is not already used
                    if (! IDInfoDbService.validateId(IDInfoDbService.extractIdFromBox(box), Optional.empty())){
                        System.out.println("Error during transaction validation: The token ID already exists!");
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
            boolean authorized = false;
            for (BoxUnlocker<Proposition> input : transaction.unlockers()) {
                for (String publicKey : this.creator) {
                    System.out.println("PublicKey "+publicKey);
                    PublicKey25519Proposition testProposition = PublicKey25519PropositionSerializer.getSerializer()
                            .parseBytes(BytesUtils.fromHexString(publicKey));
                    if (testProposition.verify(transaction.messageToSign(),input.boxKey().bytes())) {
                        System.out.println("Authorized for: "+publicKey);
                        authorized = true;
                    }
                }
            }
            if(!authorized) {
                System.out.println("Not authorized!");
                return false;
            }
            // Check that the max limit of token is not reached
            for (String key : typeCount.keySet()) {
                if(IDInfoDbService.getTypeCount(key) + typeCount.get(key) > this.maxTokenPerType.get(key)) {
                    System.out.println("Error: Exceed the maximum number of tokens that can be created inside the transaction!");
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Try<ApplicationState> onApplyChanges(SidechainStateReader stateReader,
                                                byte[] version,
                                                List<Box<Proposition>> newBoxes, List<byte[]> boxIdsToRemove) {
        //we update the Car info database. The data from it will be used during validation.

        //collect the vin to be added: the ones declared in new boxes
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
