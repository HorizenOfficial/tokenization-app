package io.horizen.tokenization.token.api.request;

/**
 * '/tokenApi/createTokenSellOrder' requests body representing class.
 */
public class CreateTokenSellOrderRequest {
    public String[] tokenBoxIds; // hex representation of token box id to sell
    public String buyerProposition; // hex representation of public key proposition
    public long sellPrice;
    public long fee;

    // Setters to let Akka jackson JSON library to automatically deserialize the request body.

    public void setTokenBoxIds(String[] tokenBoxIds) {
        this.tokenBoxIds = tokenBoxIds;
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
