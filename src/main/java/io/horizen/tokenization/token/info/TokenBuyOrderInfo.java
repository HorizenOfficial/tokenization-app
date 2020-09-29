package io.horizen.tokenization.token.info;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.box.data.RegularBoxData;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import io.horizen.tokenization.token.box.TokenSellOrderBoxSerializer;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.proof.SellOrderSpendingProof;
import io.horizen.tokenization.token.proof.SellOrderSpendingProofSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.utils.BytesUtils;

import java.util.Arrays;

// CarBuyOrderInfo contains the minimal set of data needed to construct BuyCarTransaction specific inputs an outputs.
public final class TokenBuyOrderInfo {
    private final TokenSellOrderBox tokenSellOrderBoxToOpen;  // Sell order box to be spent in BuyCarTransaction
    private final SellOrderSpendingProof proof;           // Proof to unlock the box above

    public TokenBuyOrderInfo(TokenSellOrderBox tokenSellOrderBoxToOpen, SellOrderSpendingProof proof) {
        this.tokenSellOrderBoxToOpen = tokenSellOrderBoxToOpen;
        this.proof = proof;
    }

    public TokenSellOrderBox getTokenSellOrderBoxToOpen() {
        return tokenSellOrderBoxToOpen;
    }

    public SellOrderSpendingProof getTokenSellOrderSpendingProof() {
        return proof;
    }

    // Recreates output CarBoxData with the same attributes specified in CarSellOrder.
    // Specifies the new owner depends on proof provided:
    // 1) if the proof is from the seller then the owner remain the same
    // 2) if the proof is from the buyer then it will become the new owner
    public TokenBoxData getNewOwnerTokenBoxData() {
        PublicKey25519Proposition proposition;
        if(proof.isSeller()) {
            proposition = new PublicKey25519Proposition(tokenSellOrderBoxToOpen.proposition().getOwnerPublicKeyBytes());
        } else {
            proposition = new PublicKey25519Proposition(tokenSellOrderBoxToOpen.proposition().getBuyerPublicKeyBytes());
        }

        return new TokenBoxData(
                proposition,
                tokenSellOrderBoxToOpen.getTokenId(),
                tokenSellOrderBoxToOpen.getType()
        );
    }

    // Check if proof is provided by Sell order owner.
    public boolean isSpentByOwner() {
        return proof.isSeller();
    }

    // Coins to be paid to the owner of Sell order in case if Buyer spent the Sell order.
    public RegularBoxData getPaymentBoxData() {
        return new RegularBoxData(
                new PublicKey25519Proposition(tokenSellOrderBoxToOpen.proposition().getOwnerPublicKeyBytes()),
                tokenSellOrderBoxToOpen.getPrice()
        );
    }

    // CarBuyOrderInfo minimal bytes representation.
    public byte[] bytes() {
        byte[] tokenSellOrderBoxToOpenBytes = TokenSellOrderBoxSerializer.getSerializer().toBytes(tokenSellOrderBoxToOpen);
        byte[] proofBytes = SellOrderSpendingProofSerializer.getSerializer().toBytes(proof);

        return Bytes.concat(
                Ints.toByteArray(tokenSellOrderBoxToOpenBytes.length),
                tokenSellOrderBoxToOpenBytes,
                Ints.toByteArray(proofBytes.length),
                proofBytes
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static TokenBuyOrderInfo parseBytes(byte[] bytes) {
        int offset = 0;

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        TokenSellOrderBox tokenSellOrderBoxToOpen = TokenSellOrderBoxSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        SellOrderSpendingProof proof = SellOrderSpendingProofSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new TokenBuyOrderInfo(tokenSellOrderBoxToOpen, proof);
    }
}
