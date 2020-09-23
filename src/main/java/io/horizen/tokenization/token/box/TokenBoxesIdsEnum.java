package io.horizen.tokenization.token.box;

// Declare all custom box type ids in a single enum to avoid collisions.
// Used during Boxes serializations.
public enum TokenBoxesIdsEnum {
    TokenBoxId((byte)1),
    TokenSellOrderBoxId((byte)2);

    private final byte id;

    TokenBoxesIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
