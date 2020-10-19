package io.horizen.tokenization.token.transaction;

public enum TokenTransactionsIdsEnum {
    CreateTokensTransactionId((byte)1),
    SellTokenTransactionId((byte)2),
    BuyTokenTransactionId((byte)3),
    ForgeTokenTransactionId((byte)4);

    private final byte id;

    TokenTransactionsIdsEnum(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }
}
