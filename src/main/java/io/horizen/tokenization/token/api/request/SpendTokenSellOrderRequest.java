package io.horizen.tokenization.token.api.request;

/**
 * '/tokenApi/acceptTokenSellOrder' and '/tokenApu/cancelTokenSellOrder' requests body representing class.
 */
public class SpendTokenSellOrderRequest {

    public String tokenSellOrderId; // hex representation of sellorder id
    public long fee;


    public void setTokenSellOrderId(String tokenSellOrderId) {
        this.tokenSellOrderId = tokenSellOrderId;
    }

    public void setFee(long fee) {
        this.fee = fee;
    }
}
