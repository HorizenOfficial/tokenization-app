package io.horizen.tokenization.token.box;

import com.horizen.box.BoxSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class TokenBoxSerializer implements BoxSerializer<TokenBox> {

    private static final TokenBoxSerializer serializer = new TokenBoxSerializer();

    private TokenBoxSerializer() {
        super();
    }

    public static TokenBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(TokenBox box, Writer writer) {
        writer.putBytes(box.bytes());
    }

    @Override
    public TokenBox parse(Reader reader) {
        return TokenBox.parseBytes(reader.getBytes(reader.remaining()));
    }

}
