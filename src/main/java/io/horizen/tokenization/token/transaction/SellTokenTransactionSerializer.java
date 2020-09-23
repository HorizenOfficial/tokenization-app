package io.horizen.tokenization.token.transaction;

import com.horizen.transaction.TransactionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class SellTokenTransactionSerializer implements TransactionSerializer<SellTokenTransaction> {

    private static SellTokenTransactionSerializer serializer = new SellTokenTransactionSerializer();

    private SellTokenTransactionSerializer() {
        super();
    }

    public static SellTokenTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(SellTokenTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public SellTokenTransaction parse(Reader reader) {
        return SellTokenTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}
