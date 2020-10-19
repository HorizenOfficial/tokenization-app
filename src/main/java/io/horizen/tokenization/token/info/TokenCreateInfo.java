package io.horizen.tokenization.token.info;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import java.util.Arrays;

/**
 * Additional info stored with the CreateToken transaction.
 * It contains the signature of the transaction with one of the creator public keys sepcified in the config file.
 * It will be used during transaction validation, to check that the transaction creator owned at least one private
 * key of one of the public creator keys.
 */
public class TokenCreateInfo {

    byte[]  creatorSignature;

    public TokenCreateInfo(byte[] creatorSignature){
        this.creatorSignature = creatorSignature;
    }

    // TokenCreateInfo minimal bytes representation.
    public byte[] bytes() {
        return Bytes.concat(
                Ints.toByteArray(creatorSignature.length),
                creatorSignature
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static TokenCreateInfo parseBytes(byte[] bytes) {
        int offset = 0;
        int size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;
        return new TokenCreateInfo(Arrays.copyOfRange(bytes, offset, offset + size));
    }

    public byte[] getCreatorSignature() {
        return creatorSignature;
    }
}
