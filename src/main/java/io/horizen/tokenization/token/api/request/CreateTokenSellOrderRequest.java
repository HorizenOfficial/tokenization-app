package io.horizen.tokenization.token.api.request;

// '.../carApi/acceptCarSellOrder' and '.../carApi/cancelCarSellOrder'  HTTP Post requests body representing class.
public class CreateTokenSellOrderRequest {
    public String tokenBoxId; // hex representation of box id
    public String buyerProposition; // hex representation of public key proposition
    public long sellPrice;
    public long fee;

    // Setters to let Akka jackson JSON library to automatically deserialize the request body.

    public void setTokenBoxId(String tokenBoxId) {
        this.tokenBoxId = tokenBoxId;
    }

    public void setBuyerProposition(String buyerProposition) {
        this.buyerProposition = buyerProposition;
    }

    public void setSellPrice(long sellPrice) {
        this.sellPrice = sellPrice;
    }

    public void setFee(int fee) {
        this.fee = fee;
    }
}
