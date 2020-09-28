package io.horizen.tokenization.token.box;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.AbstractNoncedBox;
import com.horizen.box.BoxSerializer;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.serialization.Views;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.box.data.TokenBoxDataSerializer;

import java.util.Arrays;

import static io.horizen.tokenization.token.box.TokenBoxesIdsEnum.TokenBoxId;


@JsonView(Views.Default.class)
@JsonIgnoreProperties({"value"})
public final class TokenBox extends AbstractNoncedBox<PublicKey25519Proposition, TokenBoxData, TokenBox> {

    public TokenBox(TokenBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public BoxSerializer serializer() {
        return TokenBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return TokenBoxId.id();
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                Longs.toByteArray(nonce),
                TokenBoxDataSerializer.getSerializer().toBytes(boxData)
        );
    }

    public static TokenBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        TokenBoxData boxData = TokenBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new TokenBox(boxData, nonce);
    }


    public String getID() {
        return boxData.getID();
    }

    public String getType() {
        return boxData.getType();
    }

}
