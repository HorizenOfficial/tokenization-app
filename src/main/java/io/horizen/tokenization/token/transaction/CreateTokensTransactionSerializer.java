package io.horizen.tokenization.token.transaction;

import com.horizen.transaction.TransactionSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class CreateTokensTransactionSerializer implements TransactionSerializer<CreateTokensTransaction> {

    private static final CreateTokensTransactionSerializer serializer = new CreateTokensTransactionSerializer();

    private CreateTokensTransactionSerializer() {
        super();
    }

    public static CreateTokensTransactionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(CreateTokensTransaction transaction, Writer writer) {
        writer.putBytes(transaction.bytes());
    }

    @Override
    public CreateTokensTransaction parse(Reader reader) {
        return CreateTokensTransaction.parseBytes(reader.getBytes(reader.remaining()));
    }
}
