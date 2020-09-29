package io.horizen.tokenization.token.info;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenBoxSerializer;
import io.horizen.tokenization.token.box.data.TokenSellOrderBoxData;
import io.horizen.tokenization.token.proposition.SellOrderProposition;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

// CarBuyOrderInfo contains the minimal set of data needed to construct SellCarTransaction specific inputs an outputs.
public final class TokenSellOrderInfo {

    private final TokenBox tokenBoxToOpen;    // Car box to be spent in SellCarTransaction
    private final Signature25519 proof;   // Proof to unlock the box above
    private final long price;             // The Car price specified by the owner
    private final PublicKey25519Proposition buyerProposition; // The potential buyer of the car.

    public TokenSellOrderInfo(TokenBox tokenBoxToOpen, Signature25519 proof, long price, PublicKey25519Proposition buyerProposition) {
        this.tokenBoxToOpen = tokenBoxToOpen;
        this.proof = proof;
        this.price = price;
        this.buyerProposition = buyerProposition;
    }

    // Input box data for unlocker construction.
    public TokenBox getTokenBoxToOpen() {
        return tokenBoxToOpen;
    }
    // Input proof data for unlocker construction.
    public Signature25519 getCarBoxSpendingProof() {
        return proof;
    }

    // Recreates output CarSellOrderBoxData with the same Car attributes specified in CarBox
    // and price/buyer specified in current CarSellOrderInfo instance.
    public TokenSellOrderBoxData getSellOrderBoxData() {
        return new TokenSellOrderBoxData(
                new SellOrderProposition(tokenBoxToOpen.proposition().pubKeyBytes(), buyerProposition.pubKeyBytes()),
                price,
                tokenBoxToOpen.getTokenId(),
                tokenBoxToOpen.getType()
        );
    }

    // CarSellOrderInfo minimal bytes representation.
    public byte[] bytes() {
        byte[] tokenBoxToOpenBytes = TokenBoxSerializer.getSerializer().toBytes(tokenBoxToOpen);
        byte[] proofBytes = Signature25519Serializer.getSerializer().toBytes(proof);

        byte[] buyerPropositionBytes = PublicKey25519PropositionSerializer.getSerializer().toBytes(buyerProposition);

        return Bytes.concat(
                Ints.toByteArray(tokenBoxToOpenBytes.length),
                tokenBoxToOpenBytes,
                Ints.toByteArray(proofBytes.length),
                proofBytes,
                Longs.toByteArray(price),
                Ints.toByteArray(buyerPropositionBytes.length),
                buyerPropositionBytes
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static TokenSellOrderInfo parseBytes(byte[] bytes) {
        int offset = 0;

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        TokenBox tokenBoxToOpen = TokenBoxSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        Signature25519 proof = Signature25519Serializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        long price = BytesUtils.getLong(bytes, offset);
        offset += 8;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        PublicKey25519Proposition buyerProposition = PublicKey25519PropositionSerializer.getSerializer()
                .parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new TokenSellOrderInfo(tokenBoxToOpen, proof, price, buyerProposition);
    }
}
