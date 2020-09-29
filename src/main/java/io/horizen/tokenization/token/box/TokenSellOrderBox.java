package io.horizen.tokenization.token.box;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.horizen.box.AbstractNoncedBox;
import com.horizen.box.BoxSerializer;
import io.horizen.tokenization.token.box.data.TokenSellOrderBoxData;
import io.horizen.tokenization.token.box.data.TokenSellOrderBoxDataSerializer;
import io.horizen.tokenization.token.proposition.SellOrderProposition;
import com.horizen.serialization.Views;

import java.util.Arrays;

// Declare default JSON view for CarSellOrderBox object. Will automatically collect all getters except ignored ones.
@JsonView(Views.Default.class)
@JsonIgnoreProperties({"boxData", "carId"})
public final class TokenSellOrderBox extends AbstractNoncedBox<SellOrderProposition, TokenSellOrderBoxData, TokenSellOrderBox> {

    public TokenSellOrderBox(TokenSellOrderBoxData boxData, long nonce) {
        super(boxData, nonce);
    }

    @Override
    public byte[] bytes() {
        return Bytes.concat(
                Longs.toByteArray(nonce),
                TokenSellOrderBoxDataSerializer.getSerializer().toBytes(boxData)
        );
    }

    @Override
    public BoxSerializer serializer() {
        return TokenSellOrderBoxSerializer.getSerializer();
    }

    @Override
    public byte boxTypeId() {
        return TokenBoxesIdsEnum.TokenSellOrderBoxId.id();
    }

    public static TokenSellOrderBox parseBytes(byte[] bytes) {
        long nonce = Longs.fromByteArray(Arrays.copyOf(bytes, Longs.BYTES));
        TokenSellOrderBoxData boxData = TokenSellOrderBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, Longs.BYTES, bytes.length));

        return new TokenSellOrderBox(boxData, nonce);
    }

    public TokenSellOrderBoxData getBoxData() {
        return this.boxData;
    }

    // Set sell order attributes getters, that is used to automatically construct JSON view:

    public String getTokenId() {
        return boxData.getTokenId();
    }

    public String getType() {
        return boxData.getType();
    }

    public long getPrice() {
        return value();
    }


}
