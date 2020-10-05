package io.horizen.tokenization.token.api;

import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.horizen.api.http.ApiResponse;
import com.horizen.api.http.ApplicationApiGroup;
import com.horizen.api.http.ErrorResponse;
import com.horizen.api.http.SuccessResponse;
import com.horizen.box.Box;
import com.horizen.box.RegularBox;
import com.horizen.box.data.RegularBoxData;
import com.horizen.companion.SidechainTransactionsCompanion;
import com.horizen.node.NodeMemoryPool;
import com.horizen.node.SidechainNodeView;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.secret.Secret;
import com.horizen.serialization.Views;
import com.horizen.transaction.BoxTransaction;
import com.horizen.utils.ByteArrayWrapper;
import com.horizen.utils.BytesUtils;
import com.typesafe.config.Config;
import io.horizen.tokenization.token.api.request.*;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.info.TokenBuyOrderInfo;
import io.horizen.tokenization.token.info.TokenCreateInfo;
import io.horizen.tokenization.token.info.TokenSellOrderInfo;
import io.horizen.tokenization.token.proof.SellOrderSpendingProof;
import io.horizen.tokenization.token.services.IDInfoDBService;
import io.horizen.tokenization.token.transaction.*;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import scala.Option;
import scala.Some;
import scorex.crypto.hash.Blake2b256;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * That class provide routes for the creation of custom transactions
 * All created here transaction are NOT moved to memoryPool, just hex representation of transaction is returned.
 * For adding transaction into memory pool core API /transaction/sendTransaction shall be used.
 * For example, for given hex transaction representation "7f0...800" next curl command could be used for adding that transaction into memory pool:
 * curl --location --request POST '127.0.0.1:9085/transaction/sendTransaction' \
 * --header 'Content-Type: application/json' \
 * --data-raw '{
 *     "transactionBytes": "7f0...800"
 * }'
 * where 127.0.0.1:9085 is API endpoint according current config file
 */
public class TokenApi extends ApplicationApiGroup {

    private final SidechainTransactionsCompanion sidechainTransactionsCompanion;
    private IDInfoDBService IDInfoDBService;
    private ArrayList<String> creator;
    private HashMap<String,Integer >maxTokenPerType;

    @Inject
    public TokenApi(@Named("SidechainTransactionsCompanion") SidechainTransactionsCompanion sidechainTransactionsCompanion, IDInfoDBService IDInfoDBService,
                    @Named("ConfigTokenizationApp") Config config) {
        this.sidechainTransactionsCompanion = sidechainTransactionsCompanion;
        this.IDInfoDBService = IDInfoDBService;
        this.creator = (ArrayList<String>) config.getObject("token").get("creatorPropositions").unwrapped();
        this.maxTokenPerType = (HashMap<String,Integer>) config.getObject("token").get("typeLimit").unwrapped();
    }

    // Define the base path for API url, i.e. according current config we could access that Api Group by using address 127.0.0.1:9085/tokenApi
    @Override
    public String basePath() {
        return "tokenApi";
    }

    // Add routes to be processed by API server.
    @Override
    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();

        //First parameter in bindPostRequest is endpoint path, for example for bindPostRequest("createTokens", ......)
        //it is 127.0.0.1:9085/tokenApi/createTokens according current config
        routes.add(bindPostRequest("createTokens", this::createTokens, CreateTokensRequest.class));
        routes.add(bindPostRequest("createTokenSellOrder", this::createTokenSellOrder, CreateTokenSellOrderRequest.class));
        routes.add(bindPostRequest("acceptTokenSellOrder", this::acceptTokenSellOrder, SpendTokenSellOrderRequest.class));
        routes.add(bindPostRequest("cancelTokenSellOrder", this::cancelTokenSellOrder, SpendTokenSellOrderRequest.class));
        routes.add(bindPostRequest("supply", this::supply));
        return routes;
    }

    private ApiResponse supply(SidechainNodeView view) {
        SupplyItem[] items = new SupplyItem[this.maxTokenPerType.keySet().size()];
        int index = 0;
        for (String tokenType: this.maxTokenPerType.keySet()){
            int typeCount = IDInfoDBService.getTypeCount(tokenType);
            items[index] = new SupplyItem(tokenType, typeCount, this.maxTokenPerType.get(tokenType));
            index++;
        }
        return new SupplyResponse(items);
    }

    private ApiResponse createTokens(SidechainNodeView view, CreateTokensRequest ent) {
        try {
            // Parse the proposition of the Token owner.
            PublicKey25519Proposition ownershipProposition = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString(ent.proposition));


            // Check that the proposition is included in the list of propositions in config that are able to create tokens.
            if (!this.creator.contains(ent.proposition)) {
                throw new IllegalStateException("This proposition is not allowed to create token!");
            }

            // Check that the token type is correct
            if (!this.maxTokenPerType.containsKey(ent.type)) {
                throw new IllegalStateException("Token type not allowed");
            }

            // Check that the max number of tokens has not yet been reached
            if(IDInfoDBService.getTypeCount(ent.type) + ent.numberOfTokens > this.maxTokenPerType.get(ent.type)) {
                throw new IllegalStateException("Maximum number of tokens reached for this type");
            }


           TokenBoxData[] tokenBoxData = new TokenBoxData[ent.numberOfTokens];
            for (int i = 0; i < ent.numberOfTokens; i++) {
                String id =  this.generateTokenId(view, i);
                tokenBoxData[i] = new TokenBoxData(ownershipProposition, id, ent.type);
            }

            // Try to collect regular boxes to pay fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = ent.fee;

            // Avoid to add boxes that are already spent in some Transaction that is present in node Mempool.
            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> regularBoxes = view.getNodeWallet().boxesOfType(RegularBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < regularBoxes.size()) {
                paymentBoxes.add(regularBoxes.get(index));
                amountToPay -= regularBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<RegularBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new RegularBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            // Creation of real proof requires transaction bytes. Transaction creation function, in turn, requires some proofs.
            // Thus real transaction creation is done in next steps:
            // 1. Create some fake/empty proofs,
            // 2. Create transaction by using those fake proofs
            // 3. Receive Tx message to be signed from transaction at step 2 (we could get it because proofs are not included into message to be signed)
            // 4. Create real proof by using Tx message to be signed
            // 5. Create real transaction with real proofs

            // Create fake proofs to be able to create transaction to be signed.
            List<byte[]> inputIds = new ArrayList<>();
            for (Box b : paymentBoxes) {
                inputIds.add(b.id());
            }

            List fakeProofs = Collections.nCopies(inputIds.size(), null);
            Long timestamp = System.currentTimeMillis();

            TokenCreateInfo fakeTokenCreateInfo = new TokenCreateInfo(new byte[0]);

            CreateTokensTransaction unsignedTransaction = new CreateTokensTransaction(
                    inputIds,
                    fakeProofs,
                    regularOutputs,
                    tokenBoxData,
                    fakeTokenCreateInfo,
                    ent.fee,
                    timestamp);

            // Get the Tx message to be signed.
            byte[] messageToSign = unsignedTransaction.messageToSign();

            // Create real signatures.
            List<Signature25519> proofs = new ArrayList<>();
            for (Box<Proposition> box : paymentBoxes) {
                proofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }


            PublicKey25519Proposition creatorProposition = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString((ent.proposition)));
            byte[] creatorSignature = (view.getNodeWallet().secretByPublicKey(creatorProposition).get().sign(messageToSign).bytes());
            TokenCreateInfo tokenCreateInfo = new TokenCreateInfo(creatorSignature);

            // Create the transaction with real proofs.
            CreateTokensTransaction signedTransaction = new CreateTokensTransaction(
                    inputIds,
                    proofs,
                    regularOutputs,
                    tokenBoxData,
                    tokenCreateInfo,
                    ent.fee,
                    timestamp);

            return new TxResponse(ByteUtils.toHexString(sidechainTransactionsCompanion.toBytes((BoxTransaction) signedTransaction)));
        }
        catch (Exception e) {
            return new TokenResponseError("0102", "Error during Token creation.", Some.apply(e));
        }
    }

    private ApiResponse createTokenSellOrder(SidechainNodeView view, CreateTokenSellOrderRequest ent) {
        try {
            // Try to find TokenBox to be opened in the closed boxes list
            TokenBox[] tokenBoxes = new TokenBox[ent.tokenBoxIds.length];
            Signature25519[] boxProofs = new Signature25519[ent.tokenBoxIds.length];
            for (int i = 0; i < ent.tokenBoxIds.length; i++) {
               String boxid = ent.tokenBoxIds[i];
                for (Box b : view.getNodeWallet().boxesOfType(TokenBox.class)) {
                    if (Arrays.equals(b.id(), BytesUtils.fromHexString(boxid))){
                        tokenBoxes[i] = (TokenBox) b;
                        break;
                    }
                }
                if (tokenBoxes[i]  == null){
                    throw new IllegalArgumentException("TokenBox with given id "+boxid+" not found in the Wallet.");
                }
            }
            // Parse the proposition of the buyer.
            PublicKey25519Proposition tokenBuyerProposition = PublicKey25519PropositionSerializer.getSerializer()
                    .parseBytes(BytesUtils.fromHexString(ent.buyerProposition));

            // Try to collect regular boxes to pay fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = ent.fee;
            // Avoid to add boxes that are already spent in some Transaction that is present in node Mempool.
            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> regularBoxes = view.getNodeWallet().boxesOfType(RegularBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < regularBoxes.size()) {
                paymentBoxes.add(regularBoxes.get(index));
                amountToPay -= regularBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<RegularBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new RegularBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            List<byte[]> inputRegularBoxIds = new ArrayList<>();
            for (Box b : paymentBoxes) {
                inputRegularBoxIds.add(b.id());
            }

            // Create fake proofs to be able to create transaction to be signed.
            TokenSellOrderInfo fakeSaleOrderInfo = new TokenSellOrderInfo(tokenBoxes, boxProofs, ent.sellPrice, tokenBuyerProposition);
            List<Signature25519> fakeRegularInputProofs = Collections.nCopies(inputRegularBoxIds.size(), null);

            Long timestamp = System.currentTimeMillis();

            SellTokenTransaction unsignedTransaction = new SellTokenTransaction(
                    inputRegularBoxIds,
                    fakeRegularInputProofs,
                    regularOutputs,
                    fakeSaleOrderInfo,
                    ent.fee,
                    timestamp);

            // Get the Tx message to be signed.
            byte[] messageToSign = unsignedTransaction.messageToSign();

            // Create signatures.
            List<Signature25519> regularInputProofs = new ArrayList<>();

            for (Box<Proposition> box : paymentBoxes) {
                regularInputProofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }

            for (int i = 0; i < tokenBoxes.length; i++){
                boxProofs[i] =  (Signature25519)view.getNodeWallet().secretByPublicKey(tokenBoxes[i].proposition()).get().sign(messageToSign);
            }

            TokenSellOrderInfo saleOrderInfo = new TokenSellOrderInfo(
                    tokenBoxes,
                    boxProofs,
                    ent.sellPrice,
                    tokenBuyerProposition);


            // Create the resulting signed transaction.
            SellTokenTransaction transaction = new SellTokenTransaction(
                    inputRegularBoxIds,
                    regularInputProofs,
                    regularOutputs,
                    saleOrderInfo,
                    ent.fee,
                    timestamp);

            return new TxResponse(ByteUtils.toHexString(sidechainTransactionsCompanion.toBytes((BoxTransaction) transaction)));
        }
        catch (Exception e) {
            return new TokenResponseError("0102", "Error during Token Sell Order sell operation.", Some.apply(e));
        }
    }

    private ApiResponse acceptTokenSellOrder(SidechainNodeView view, SpendTokenSellOrderRequest ent) {
        try {
            // Try to find a sell order to be opened in the closed boxes list
            Optional sellORder = view.getNodeState().getClosedBox(BytesUtils.fromHexString(ent.tokenSellOrderId));
            if (!sellORder.isPresent()){
                return new TokenResponseError("0101", "Sell order not found", Option.empty());
            }
            TokenSellOrderBox tokenSellOrderBox = (TokenSellOrderBox)sellORder.get();

            // Check that sell order buyer public key is controlled by node wallet.
            Optional<Secret> buyerSecretOption = view.getNodeWallet().secretByPublicKey(
                    new PublicKey25519Proposition(tokenSellOrderBox.proposition().getBuyerPublicKeyBytes()));
            if(!buyerSecretOption.isPresent()) {
                return new TokenResponseError("0100", "Can'accept this sell order, because the buyer proposition is not owned by the Node.", Option.empty());
            }

            // Get Regular boxes to pay the order price + fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = tokenSellOrderBox.getPrice() + ent.fee;

            // Avoid to add boxes that are already spent in some Transaction that is present in node Mempool.
            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> regularBoxes = view.getNodeWallet().boxesOfType(RegularBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < regularBoxes.size()) {
                paymentBoxes.add(regularBoxes.get(index));
                amountToPay -= regularBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<RegularBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new RegularBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            List<byte[]> inputRegularBoxIds = new ArrayList<>();
            for (Box b : paymentBoxes) {
                inputRegularBoxIds.add(b.id());
            }

            // Create fake proofs to be able to create transaction to be signed.
            // Specify that sell order is not opened by the seller, but opened by the buyer.
            boolean isSeller = false;
            SellOrderSpendingProof fakeSellProof = new SellOrderSpendingProof(new byte[SellOrderSpendingProof.SIGNATURE_LENGTH], isSeller);
            TokenBuyOrderInfo fakeBuyOrderInfo = new TokenBuyOrderInfo(tokenSellOrderBox, fakeSellProof);

            List<Signature25519> fakeRegularInputProofs = Collections.nCopies(inputRegularBoxIds.size(), null);
            Long timestamp = System.currentTimeMillis();

            BuyTokenTransaction unsignedTransaction = new BuyTokenTransaction(
                    inputRegularBoxIds,
                    fakeRegularInputProofs,
                    regularOutputs,
                    fakeBuyOrderInfo,
                    ent.fee,
                    timestamp);

            // Get the Tx message to be signed.
            byte[] messageToSign = unsignedTransaction.messageToSign();

            // Create regular signatures.
            List<Signature25519> regularInputProofs = new ArrayList<>();
            for (Box<Proposition> box : paymentBoxes) {
                regularInputProofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }

            // Create sell order spending proof for buyer
            SellOrderSpendingProof buyerProof = new SellOrderSpendingProof(
                    buyerSecretOption.get().sign(messageToSign).bytes(),
                    isSeller
            );

            // Create the resulting signed transaction.
            TokenBuyOrderInfo buyOrderInfo = new TokenBuyOrderInfo(tokenSellOrderBox, buyerProof);

            BuyTokenTransaction transaction = new BuyTokenTransaction(
                    inputRegularBoxIds,
                    regularInputProofs,
                    regularOutputs,
                    buyOrderInfo,
                    ent.fee,
                    timestamp);

            return new TxResponse(ByteUtils.toHexString(sidechainTransactionsCompanion.toBytes((BoxTransaction) transaction)));
        } catch (Exception e) {
            return new TokenResponseError("0103", "Error during Token Sell Order buy operation.", Some.apply(e));
        }
    }

    private ApiResponse cancelTokenSellOrder(SidechainNodeView view, SpendTokenSellOrderRequest ent) {
        try {
            // Try to find a sell order to be opened in the closed boxes list
            Optional<Box> tokenSellOrderBoxOption = view.getNodeState().getClosedBox(BytesUtils.fromHexString(ent.tokenSellOrderId));

            if (!tokenSellOrderBoxOption.isPresent()) {
                throw new IllegalArgumentException("TokenSellOrderBox with given box id not found in the State.");
            }

            TokenSellOrderBox tokenSellOrderBox = (TokenSellOrderBox)tokenSellOrderBoxOption.get();

            // Check that sell order owner public key is controlled by node wallet.
            Optional<Secret> ownerSecretOption = view.getNodeWallet().secretByPublicKey(
                    new PublicKey25519Proposition(tokenSellOrderBox.proposition().getOwnerPublicKeyBytes()));
            if(!ownerSecretOption.isPresent()) {
                return new TokenResponseError("0100", "Can't cancel this sell order, because the owner proposition is not owned by the Node.", Option.empty());
            }

            // Get Regular boxes to pay the fee
            List<Box<Proposition>> paymentBoxes = new ArrayList<>();
            long amountToPay = ent.fee;

            List<byte[]> boxIdsToExclude = boxesFromMempool(view.getNodeMemoryPool());
            List<Box<Proposition>> regularBoxes = view.getNodeWallet().boxesOfType(RegularBox.class, boxIdsToExclude);
            int index = 0;
            while (amountToPay > 0 && index < regularBoxes.size()) {
                paymentBoxes.add(regularBoxes.get(index));
                amountToPay -= regularBoxes.get(index).value();
                index++;
            }

            if (amountToPay > 0) {
                throw new IllegalStateException("Not enough coins to pay the fee.");
            }

            // Set change if exists
            long change = Math.abs(amountToPay);
            List<RegularBoxData> regularOutputs = new ArrayList<>();
            if (change > 0) {
                regularOutputs.add(new RegularBoxData((PublicKey25519Proposition) paymentBoxes.get(0).proposition(), change));
            }

            List<byte[]> inputRegularBoxIds = new ArrayList<>();
            for (Box b : paymentBoxes) {
                inputRegularBoxIds.add(b.id());
            }

            // Create fake proofs to be able to create transaction to be signed.
            // Specify that sell order is opened by the seller.
            boolean isSeller = true;
            SellOrderSpendingProof fakeOwnerProof = new SellOrderSpendingProof(new byte[SellOrderSpendingProof.SIGNATURE_LENGTH], isSeller);
            TokenBuyOrderInfo fakeBuyOrderInfo = new TokenBuyOrderInfo(tokenSellOrderBox, fakeOwnerProof);

            List<Signature25519> fakeRegularInputProofs = Collections.nCopies(inputRegularBoxIds.size(), null);
            Long timestamp = System.currentTimeMillis();

            BuyTokenTransaction unsignedTransaction = new BuyTokenTransaction(
                    inputRegularBoxIds,
                    fakeRegularInputProofs,
                    regularOutputs,
                    fakeBuyOrderInfo,
                    ent.fee,
                    timestamp);

            // Get the Tx message to be signed.
            byte[] messageToSign = unsignedTransaction.messageToSign();

            // Create regular signatures.
            List<Signature25519> regularInputProofs = new ArrayList<>();
            for (Box<Proposition> box : paymentBoxes) {
                regularInputProofs.add((Signature25519) view.getNodeWallet().secretByPublicKey(box.proposition()).get().sign(messageToSign));
            }

            // Create sell order spending proof for owner
            SellOrderSpendingProof ownerProof = new SellOrderSpendingProof(
                    ownerSecretOption.get().sign(messageToSign).bytes(),
                    isSeller
            );

            // Create the resulting signed transaction.
            TokenBuyOrderInfo buyOrderInfo = new TokenBuyOrderInfo(tokenSellOrderBox, ownerProof);

            BuyTokenTransaction transaction = new BuyTokenTransaction(
                    inputRegularBoxIds,
                    regularInputProofs,
                    regularOutputs,
                    buyOrderInfo,
                    ent.fee,
                    timestamp);

            return new TxResponse(ByteUtils.toHexString(sidechainTransactionsCompanion.toBytes((BoxTransaction) transaction)));
        } catch (Exception e) {
            return new TokenResponseError("0103", "Error during Token Sell Order cancel operation.", Some.apply(e));
        }
    }

    // The api requests success result output structure.
    @JsonView(Views.Default.class)
    static class SupplyResponse implements SuccessResponse {
        public SupplyItem[] supply;

        public SupplyResponse(SupplyItem[] supply) {
            this.supply = supply;
        }
        
        public SupplyItem[] getSupply(){
            return supply;
        }
    }
    @JsonView(Views.Default.class)
    static class SupplyItem {
        public String tokenType;
        public int forged;
        public int maxSupply;

        public SupplyItem(String tokenType, int forged, int maxSupply) {
            this.tokenType = tokenType;
            this.maxSupply = maxSupply;
            this.forged = forged;
        }

        public String getTokenType(){
            return tokenType;
        }

        public int getForged() {
            return forged;
        }
        public int getMaxSupply() {
            return maxSupply;
        }
    }

    @JsonView(Views.Default.class)
    static class TxResponse implements SuccessResponse {
        public String transactionBytes;

        public TxResponse(String transactionBytes) {
            this.transactionBytes = transactionBytes;
        }
    }


    static class TokenResponseError implements ErrorResponse {
        private final String code;
        private final String description;
        private final Option<Throwable> exception;

        TokenResponseError(String code, String description, Option<Throwable> exception) {
            this.code = code;
            this.description = description;
            this.exception = exception;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public Option<Throwable> exception() {
            return exception;
        }
    }

    // generates a random tokenid  of 20 characters and check that is unique (both in local id store and in mempool).
    private String generateTokenId(SidechainNodeView view, int nonce){
        String id = null;
        do {
            byte[] hash = Blake2b256.hash(Bytes.concat(Longs.toByteArray(new Date().getTime()), Ints.toByteArray(nonce)));
            id = BytesUtils.toHexString(hash).substring(0, 20);
        } while (!IDInfoDBService.validateId(id, Optional.of(view.getNodeMemoryPool())));
        return id;
    }

    // Utility functions to get from the current mempool the list of all boxes to be opened.
    private List<byte[]> boxesFromMempool(NodeMemoryPool mempool) {
        List<byte[]> boxesFromMempool = new ArrayList<>();
        for(BoxTransaction tx : mempool.getTransactions()) {
            Set<ByteArrayWrapper> ids = tx.boxIdsToOpen();
            for(ByteArrayWrapper id : ids) {
                boxesFromMempool.add(id.data());
            }
        }
        return boxesFromMempool;
    }
}

