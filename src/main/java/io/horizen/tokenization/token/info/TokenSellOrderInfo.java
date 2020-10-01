package io.horizen.tokenization.token.info;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenBoxSerializer;
import io.horizen.tokenization.token.box.data.TokenSellOrderBoxData;
import io.horizen.tokenization.token.box.data.TokenSellOrderItem;
import io.horizen.tokenization.token.proposition.SellOrderProposition;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.utils.BytesUtils;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

// TokenSellOrderInfo contains the minimal set of data needed to construct SellTokenTransaction specific inputs an outputs.
public final class TokenSellOrderInfo {

    private final TokenBox[] tokenBoxesToOpen;    // token boxes sold in this sell order
    private final Signature25519[] proofs;   // Proof to unlock the boxes above
    private final long price;             // The overall order price specified by the owner
    private final PublicKey25519Proposition buyerProposition; // The potential buyer

    public TokenSellOrderInfo(TokenBox[] tokenBoxesToOpen, Signature25519[] proofs, long price, PublicKey25519Proposition buyerProposition) {
        this.tokenBoxesToOpen = tokenBoxesToOpen;
        this.proofs = proofs;
        this.price = price;
        this.buyerProposition = buyerProposition;
    }

    // Input box data for unlocker construction.
    public TokenBox getTokenBoxToOpen(int index) {
        return tokenBoxesToOpen[index];
    }

    // Input proof data for unlocker construction.
    public Signature25519 getTokenBoxSpendingProof(int index) {
        return proofs[index];
    }

    public int getTotalTokensToSell(){
        return tokenBoxesToOpen.length;
    }


    public TokenSellOrderBoxData getSellOrderBoxData() {
        SellOrderProposition prop = new SellOrderProposition(tokenBoxesToOpen[0].proposition().pubKeyBytes(), buyerProposition.pubKeyBytes());
        TokenSellOrderItem[] items = new TokenSellOrderItem[tokenBoxesToOpen.length];
        for (int i = 0; i < tokenBoxesToOpen.length; i++){
            TokenBox box = tokenBoxesToOpen[i];
            items[i] = new TokenSellOrderItem(box.getTokenId(), box.getType(), box.proposition());
        }
        return new TokenSellOrderBoxData(
                prop,
                price,
                items
        );
    }

    // TokenSellOrderInfo minimal bytes representation.
    public byte[] bytes() {
        ByteArrayOutputStream tokenBoxesToOpenStream = new ByteArrayOutputStream();
        for(int i = 0; i < tokenBoxesToOpen.length; i++){
            TokenBox box = tokenBoxesToOpen[i];
            Signature25519 proof = proofs[i];
            byte[] tokenBoxToOpenBytes = TokenBoxSerializer.getSerializer().toBytes(box);
            byte[] tokenBoxToOpenBytesLength = Ints.toByteArray(tokenBoxToOpenBytes.length);
            byte[] proofBytes = Signature25519Serializer.getSerializer().toBytes(proof);
            byte[] proofBytesLength = Ints.toByteArray(proofBytes.length);
            tokenBoxesToOpenStream.write(tokenBoxToOpenBytesLength, 0, tokenBoxToOpenBytesLength.length);
            tokenBoxesToOpenStream.write(tokenBoxToOpenBytes, 0, tokenBoxToOpenBytes.length);
            tokenBoxesToOpenStream.write(proofBytesLength, 0, proofBytesLength.length);
            tokenBoxesToOpenStream.write(proofBytes, 0, proofBytes.length);
        }
        byte[] tokenBoxesToOpenBytes =  tokenBoxesToOpenStream.toByteArray();
        byte[] buyerPropositionBytes = PublicKey25519PropositionSerializer.getSerializer().toBytes(buyerProposition);
        return Bytes.concat(
                Ints.toByteArray(tokenBoxesToOpen.length),
                tokenBoxesToOpenBytes,
                Longs.toByteArray(price),
                Ints.toByteArray(buyerPropositionBytes.length),
                buyerPropositionBytes
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static TokenSellOrderInfo parseBytes(byte[] bytes) {
        int offset = 0;
        int batchSize;
        int boxNum = BytesUtils.getInt(bytes, offset);
        offset += 4;
        TokenBox[] tokenBoxesToOpen = new TokenBox[boxNum];
        Signature25519[] proofs = new Signature25519[boxNum];
        for (int i = 0 ; i < boxNum; i++){

            batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            tokenBoxesToOpen[i] = TokenBox.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
            offset += batchSize;

            batchSize = BytesUtils.getInt(bytes, offset);
            offset += 4;
            proofs[i] = Signature25519Serializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
            offset += batchSize;
        }

        long price = BytesUtils.getLong(bytes, offset);
        offset += 8;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        PublicKey25519Proposition buyerProposition = PublicKey25519PropositionSerializer.getSerializer()
                .parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new TokenSellOrderInfo(tokenBoxesToOpen, proofs, price, buyerProposition);
    }
}
