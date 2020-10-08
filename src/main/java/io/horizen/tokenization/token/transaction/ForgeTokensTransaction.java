package io.horizen.tokenization.token.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.SidechainTransaction;
import com.horizen.transaction.TransactionIncompatibilityChecker;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.box.TokenBox;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.box.data.TokenBoxDataSerializer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.horizen.tokenization.token.transaction.TokenTransactionsIdsEnum.ForgeTokenTransactionId;

/**
 * ForgeTokensTransaction is used for auto-forging of new tokens: it can be created only at forge time by the block
 * forger, it don't receive any input box and create only one or more TokenBox in output. It has no fee.
 */
public final class ForgeTokensTransaction extends SidechainTransaction {


    private final TokenBoxData[] outputTokenBoxData;
    private List<NoncedBox<Proposition>> newBoxes;
    private long timestamp;

    public ForgeTokensTransaction(TokenBoxData[] outputTokenBoxData, long timestamp) {
        this.outputTokenBoxData = outputTokenBoxData;
        this.timestamp = timestamp;
    }

    // Specify the unique custom transaction id.
    @Override
    public byte transactionTypeId() { return ForgeTokenTransactionId.id(); }

    // Override newBoxes to contains regularBoxes from the parent class appended with TokenBox entries.
    @Override
    public synchronized List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>();
            for (int i=0; i<this.outputTokenBoxData.length; i++) {
                long nonce = getNewBoxNonce(outputTokenBoxData[i].proposition(), newBoxes.size());
                newBoxes.add((NoncedBox) new TokenBox(outputTokenBoxData[i], nonce));
            }
        }
        return Collections.unmodifiableList(newBoxes);
    }

    @Override
    public List<BoxUnlocker<Proposition>> unlockers() {
        return  new ArrayList<>();
    }

    // Define object serialization, that should serialize both parent class entries and TokenBoxData as well
    @Override
    public byte[] bytes() {


        ByteArrayOutputStream outputTokenStream = new ByteArrayOutputStream();
        for(TokenBoxData token: outputTokenBoxData) {
            try {
                outputTokenStream.write(Ints.toByteArray(token.bytes().length));
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputTokenStream.write(token.bytes(), 0, token.bytes().length);
        }
        byte[] outputTokenBoxDataBytes = outputTokenStream.toByteArray();
        return Bytes.concat(
                Longs.toByteArray(timestamp()),                      // 8 bytes
                Ints.toByteArray(outputTokenBoxData.length),         // 4 bytes
                outputTokenBoxDataBytes                              // depends on previous value (>=4 bytes)
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static ForgeTokensTransaction parseBytes(byte[] bytes) {
        int offset = 0;

        long timestamp = BytesUtils.getLong(bytes, offset);
        offset += 8;

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        TokenBoxData[] outputBoxData = new TokenBoxData[batchSize];
        for (int i = 0; i < batchSize; i++) {
            int size = BytesUtils.getInt(bytes, offset);
            offset += 4;
            outputBoxData[i] = (TokenBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + size)));
            offset += size;
        }
        return new ForgeTokensTransaction(outputBoxData, timestamp);
    }

    @Override
    public TransactionIncompatibilityChecker incompatibilityChecker() {
        return new ForgeTokensTransactionIncompatibilityChecker();
    }

    @Override
    public long fee() {
        return 0;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public boolean transactionSemanticValidity() {
        if (timestamp < 0)
            return false;

        return true;
    }

    // Set specific Serializer for this transaction
    @Override
    public TransactionSerializer serializer() {
        return ForgeTokensTransactionSerializer.getSerializer();
    }
}
