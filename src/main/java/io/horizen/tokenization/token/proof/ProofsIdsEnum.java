package io.horizen.tokenization.token.proof;

// Declare all custom proofs ids in a single enum to avoid collisions.
// Used during Proofs serializations.
public enum ProofsIdsEnum {

    SellOrderSpendingProofId((byte)1);

    private final byte id;
    ProofsIdsEnum(byte id) {
        this.id = id;
    }
    public byte id() {
        return id;
    }
}
