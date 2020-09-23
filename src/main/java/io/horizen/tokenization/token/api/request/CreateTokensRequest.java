package io.horizen.tokenization.token.api.request;

// '.../carApi/createCar' HTTP Post request body representing class.
public class CreateTokensRequest {
    public String type;
    public int numberOfTokens;
    public String proposition; // hex representation of public key proposition
    public long fee;


    // Setters to let Akka jackson JSON library to automatically deserialize the request body.

    public void setType(String type) {
        this.type = type;
    }

    public void setNumberOfTokens(int numberOfTokens) {
        this.numberOfTokens = numberOfTokens;
    }

    public void setProposition(String proposition) {
        this.proposition = proposition;
    }
}
