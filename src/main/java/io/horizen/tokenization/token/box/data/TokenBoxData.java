package io.horizen.tokenization.token.box.data;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.horizen.box.data.AbstractNoncedBoxData;
import com.horizen.box.data.NoncedBoxDataSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.proposition.PublicKey25519PropositionSerializer;
import com.horizen.serialization.Views;
import io.horizen.tokenization.token.box.TokenBox;
import scorex.crypto.hash.Blake2b256;

import java.util.Arrays;

import static io.horizen.tokenization.token.box.data.TokenBoxesDataIdsEnum.TokenBoxDataId;

@JsonView(Views.Default.class)
/**
 * This class specifies the properties of a token.
 * Each token has the following properties:
 * - a unique ID, set douring token creation and does not change upon selling.
 * - a type
 */
public final class TokenBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, TokenBox, TokenBoxData> {

    private final String tokenId;   // unique token id
    private final String type;


    public TokenBoxData(PublicKey25519Proposition proposition, String tokenId, String type) {
        //AbstractNoncedBoxData requires value to be set in constructor. However, our token has no value in ZEN by default. So just set value to 0
        super(proposition, 0);
        this.tokenId = tokenId;
        this.type = type;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getType() {
        return type;
    }

    @Override
    public TokenBox getBox(long nonce) {
        return new TokenBox(this, nonce);
    }

    @Override
    public byte[] customFieldsHash() {
        return Blake2b256.hash(tokenId.getBytes());
    }

    @Override
    public NoncedBoxDataSerializer serializer() {
        return TokenBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return TokenBoxDataId.id();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                proposition().bytes(),
                Ints.toByteArray(tokenId.getBytes().length),
                tokenId.getBytes(),
                Ints.toByteArray(type.getBytes().length),
                type.getBytes()
        );
    }

    public static TokenBoxData parseBytes(byte[] bytes) {
        int offset = 0;

        PublicKey25519Proposition proposition = PublicKey25519PropositionSerializer.getSerializer()
                .parseBytes(Arrays.copyOf(bytes, PublicKey25519Proposition.getLength()));
        offset += PublicKey25519Proposition.getLength();

        int size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;

        String tokenId = new String(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;

        size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;

        String type = new String(Arrays.copyOfRange(bytes, offset, offset + size));

        return new TokenBoxData(proposition, tokenId, type);
    }

    @Override
    public String toString() {
        return "TokenBoxData{" +
                "tokenId=" + tokenId +" "+
                "type= "+type+
                ", proposition=" + proposition() +
                '}';
    }
}
