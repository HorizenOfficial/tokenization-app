package io.horizen.tokenization.token.box;

import com.horizen.box.BoxSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

public final class TokenSellOrderBoxSerializer implements BoxSerializer<TokenSellOrderBox> {

    private static final TokenSellOrderBoxSerializer serializer = new TokenSellOrderBoxSerializer();

    private TokenSellOrderBoxSerializer() {
        super();
    }

    public static TokenSellOrderBoxSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(TokenSellOrderBox tokenSellOrder, Writer writer) {
        writer.putBytes(tokenSellOrder.bytes());
    }

    @Override
    public TokenSellOrderBox parse(Reader reader) {
        return TokenSellOrderBox.parseBytes(reader.getBytes(reader.remaining()));
    }
}
