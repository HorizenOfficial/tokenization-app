package io.horizen.tokenization.token.box.data;

import com.horizen.box.data.NoncedBoxDataSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class TokenSellOrderBoxDataSerializer implements NoncedBoxDataSerializer<TokenSellOrderBoxData> {

    private static final TokenSellOrderBoxDataSerializer serializer = new TokenSellOrderBoxDataSerializer();

    private TokenSellOrderBoxDataSerializer() {
        super();
    }

    public static TokenSellOrderBoxDataSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(TokenSellOrderBoxData boxData, Writer writer) {
        writer.putBytes(boxData.bytes());
    }

    @Override
    public TokenSellOrderBoxData parse(Reader reader) {
        return TokenSellOrderBoxData.parseBytes(reader.getBytes(reader.remaining()));
    }
}
