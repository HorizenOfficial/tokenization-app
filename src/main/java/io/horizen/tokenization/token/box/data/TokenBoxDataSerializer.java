package io.horizen.tokenization.token.box.data;

import com.horizen.box.data.NoncedBoxDataSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class TokenBoxDataSerializer implements NoncedBoxDataSerializer<TokenBoxData> {

    private static final TokenBoxDataSerializer serializer = new TokenBoxDataSerializer();

    private TokenBoxDataSerializer() {
        super();
    }

    public static TokenBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(TokenBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public TokenBoxData parse(Reader reader) {
        return TokenBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}
