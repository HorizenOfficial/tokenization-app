package io.horizen.tokenization.token.api.request;

/**
 * '/tokenApi/createTokens' requests body representing class.
 */
public class CreateTokensRequest {

    public String type;
    public int numberOfTokens;

    public String proposition; // hex representation of public key proposition
    public long fee;

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
