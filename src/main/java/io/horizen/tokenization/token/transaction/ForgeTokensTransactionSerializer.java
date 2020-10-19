package io.horizen.tokenization.token.transaction;

import com.horizen.transaction.TransactionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class ForgeTokensTransactionSerializer implements TransactionSerializer<ForgeTokensTransaction> {

    private static final ForgeTokensTransactionSerializer serializer = new ForgeTokensTransactionSerializer();

    private ForgeTokensTransactionSerializer() {
        super();
    }

    public static ForgeTokensTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(ForgeTokensTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public ForgeTokensTransaction parse(Reader reader) {
        return ForgeTokensTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}
