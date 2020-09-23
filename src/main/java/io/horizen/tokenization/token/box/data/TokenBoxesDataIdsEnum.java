package io.horizen.tokenization.token.box.data;

// Declare all custom box data type ids in a single enum to avoid collisions.
// Used during BoxData serializations.
public enum TokenBoxesDataIdsEnum {
    TokenBoxDataId((byte)1),
    TokenSellOrderBoxDataId((byte)2);

    private final byte id;

    TokenBoxesDataIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
