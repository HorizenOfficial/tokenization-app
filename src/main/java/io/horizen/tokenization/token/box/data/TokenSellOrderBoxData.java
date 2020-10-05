package io.horizen.tokenization.token.box.data;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.data.AbstractNoncedBoxData;
import com.horizen.box.data.NoncedBoxDataSerializer;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import io.horizen.tokenization.token.proposition.SellOrderProposition;
import io.horizen.tokenization.token.proposition.SellOrderPropositionSerializer;
import com.horizen.serialization.Views;
import scorex.crypto.hash.Blake2b256;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A Token sell order has an overall price and may contain more than one token: in this structure
 * we store for each token the id, the type, and the owner public key
 */
@JsonView(Views.Default.class)
public final class TokenSellOrderBoxData extends AbstractNoncedBoxData<SellOrderProposition, TokenSellOrderBox, TokenSellOrderBoxData> {


    private final TokenSellOrderItem[] orderItems;

    public TokenSellOrderBoxData(SellOrderProposition proposition, long price,  TokenSellOrderItem[] orderItems) {
        super(proposition, price);
        this.orderItems = orderItems;
    }

    public TokenSellOrderItem getOrderItem(int i){
        return orderItems[i];
    }
    public int getOrderItemLenght(){
        return orderItems.length;
    }

    @Override
    public TokenSellOrderBox getBox(long nonce) {
        return new TokenSellOrderBox(this, nonce);
    }

    @Override
    public byte[] customFieldsHash() {
        ByteArrayOutputStream orderItemsStream = new ByteArrayOutputStream();
        for(TokenSellOrderItem item: orderItems){
            byte[] arr = item.bytes();
            orderItemsStream.write(arr, 0, arr.length);
        }
        return Blake2b256.hash(
                orderItemsStream.toByteArray()
        );
    }

    @Override
    public NoncedBoxDataSerializer serializer() {
        return TokenSellOrderBoxDataSerializer.getSerializer();
    }

    @Override
    public byte boxDataTypeId() {
        return TokenBoxesDataIdsEnum.TokenSellOrderBoxDataId.id();
    }

    @Override
    public byte[] bytes() {

        ByteArrayOutputStream orderItemsStream = new ByteArrayOutputStream();
        for(TokenSellOrderItem item: orderItems){
            byte[] arr = item.bytes();
            byte[] arrLenght = Ints.toByteArray(arr.length);
            orderItemsStream.write(arrLenght, 0, arrLenght.length);
            orderItemsStream.write(arr, 0, arr.length);
        }
        return Bytes.concat(
                Ints.toByteArray(proposition().bytes().length),
                proposition().bytes(),
                Longs.toByteArray(value()),
                Ints.toByteArray(orderItems.length),
                orderItemsStream.toByteArray()
        );
    }

    public static TokenSellOrderBoxData parseBytes(byte[] bytes) {
        int offset = 0;

        int size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;

        SellOrderProposition proposition = SellOrderPropositionSerializer.getSerializer()
                .parseBytes(Arrays.copyOfRange(bytes, offset, offset + size));
        offset += size;

        long price = Longs.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Longs.BYTES));
        offset += Longs.BYTES;

        size = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
        offset += Ints.BYTES;

        TokenSellOrderItem[] sellItems = new TokenSellOrderItem[size];
        for (int i = 0; i < size; i++ ){
            int itemSize = Ints.fromByteArray(Arrays.copyOfRange(bytes, offset, offset + Ints.BYTES));
            offset += Ints.BYTES;

            sellItems[i] = TokenSellOrderItem.parseBytes(Arrays.copyOfRange(bytes, offset, offset + itemSize));
            offset += itemSize;
        }

        return new TokenSellOrderBoxData(proposition, price, sellItems);
    }

    @Override
    public String toString() {
        String tokensIds = "";
        for (TokenSellOrderItem item: orderItems) {
            tokensIds = tokensIds + (tokensIds.length() > 0 ? "," : "") + item.getTokenId();
        }
        return "TokenSellOrderBoxData{" +
                "  tokenId=[" + tokensIds + "]" +
                ", proposition=" + proposition() +
                ", value=" + value() +
                '}';
    }
}
