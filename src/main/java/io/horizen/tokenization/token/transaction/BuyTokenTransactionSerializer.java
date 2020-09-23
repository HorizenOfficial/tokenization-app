package io.horizen.tokenization.token.transaction;

import com.horizen.transaction.TransactionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class BuyTokenTransactionSerializer implements TransactionSerializer<BuyTokenTransaction> {

    private static final BuyTokenTransactionSerializer serializer = new BuyTokenTransactionSerializer();

    private BuyTokenTransactionSerializer() {
        super();
    }

    public static BuyTokenTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(BuyTokenTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public BuyTokenTransaction parse(Reader reader) {
        return BuyTokenTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}
