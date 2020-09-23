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
public final class TokenBoxData extends AbstractNoncedBoxData<PublicKey25519Proposition, TokenBox, TokenBoxData> {

    // In CarRegistry example we defined 4 main car attributes:
    private final String id;   // Vehicle Identification Number
    private final String type;

    // Additional check on VIN length can be done as well, but not present as a part of current example.
    public TokenBoxData(PublicKey25519Proposition proposition, String id, String type) {
        //AbstractNoncedBoxData requires value to be set in constructor. However, our car is unique object without any value in ZEN by default. So just set value to 1
        super(proposition, 0);
        this.id = id;
        this.type = type;
    }

    public String getID() {
        return id;
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
        return Blake2b256.hash(id.getBytes());
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
                Ints.toByteArray(id.getBytes().length),
                id.getBytes(),
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

        String id = new String(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;

        size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;

        String type = new String(Arrays.copyOfRange(bytes, offset, offset + size));

        return new TokenBoxData(proposition, id, type);
    }

    @Override
    public String toString() {
        return "TokenBoxData{" +
                "id=" + id +" "+
                "type= "+type+
                ", proposition=" + proposition() +
                '}';
    }
}
