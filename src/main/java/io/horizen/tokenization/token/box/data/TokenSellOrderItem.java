package io.horizen.tokenization.token.box.data;


import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.proof.Signature25519;
import com.horizen.proof.Signature25519Serializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.TokenBoxSerializer;
import io.horizen.tokenization.token.info.TokenSellOrderInfo;

import java.util.Arrays;

public class TokenSellOrderItem {

    private String tokenId;
    private String type;
    private PublicKey25519Proposition ownerProposition;

    public  TokenSellOrderItem(String tokenId, String type, PublicKey25519Proposition ownerProposition){
        this.tokenId = tokenId;
        this.type = type;
        this.ownerProposition = ownerProposition;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getType() {
        return type;
    }

    public PublicKey25519Proposition getOwnerProposition(){
        return ownerProposition;
    }


    public byte[] bytes() {
        return Bytes.concat(
                Ints.toByteArray(tokenId.getBytes().length),
                tokenId.getBytes(),
                Ints.toByteArray(type.getBytes().length),
                type.getBytes(),
                Ints.toByteArray(ownerProposition.bytes().length),
                ownerProposition.bytes()
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static TokenSellOrderItem parseBytes(byte[] bytes) {
        int offset = 0;
        int size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;
        String id = new String(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;
        size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;
        String type = new String(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;
        size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;
        PublicKey25519Proposition prop = new PublicKey25519Proposition(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;
        return new TokenSellOrderItem(id, type, prop);
    }



}
