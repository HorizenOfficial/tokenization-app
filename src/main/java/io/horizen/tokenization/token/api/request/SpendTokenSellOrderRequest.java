package io.horizen.tokenization.token.api.request;

// '.../carApi/createCar' HTTP Post request body representing class.
public class SpendTokenSellOrderRequest {
    public String tokenSellOrderId; // hex representation of box id
    public long fee;

    // Setters to let Akka jackson JSON library to automatically deserialize the request body.

    public void setTokenSellOrderId(String tokenSellOrderId) {
        this.tokenSellOrderId = tokenSellOrderId;
    }

    public void setFee(long fee) {
        this.fee = fee;
    }
}
