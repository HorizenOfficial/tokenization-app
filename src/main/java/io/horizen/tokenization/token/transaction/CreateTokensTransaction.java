package io.horizen.tokenization.token.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.NoncedBox;
import com.horizen.box.data.RegularBoxData;
import io.horizen.tokenization.token.box.TokenBox;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.box.data.TokenBoxDataSerializer;
import scorex.core.NodeViewModifier$;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.horizen.tokenization.token.transaction.TokenTransactionsIdsEnum.CreateTokensTransactionId;

// CarDeclarationTransaction is nested from AbstractRegularTransaction so support regular coins transmission as well.
// Moreover it was designed to declare new Cars in the sidechain network.
// As outputs it contains possible RegularBoxes(to pay fee and change) and new CarBox entry.
// No specific unlockers to parent class logic, but has specific new box.
// TODO: add specific mempool incompatibility checker to deprecate keeping in the Mempool txs that declare the same Car.
public final class CreateTokensTransaction extends AbstractRegularTransaction {

    private final TokenBoxData[] outputTokenBoxData;
    private List<NoncedBox<Proposition>> newBoxes;

    public CreateTokensTransaction(List<byte[]> inputRegularBoxIds,
                                   List<Signature25519> inputRegularBoxProofs,
                                   List<RegularBoxData> outputRegularBoxesData,
                                   TokenBoxData[] outputTokenBoxData,
                                   long fee,
                                   long timestamp) {
        super(inputRegularBoxIds, inputRegularBoxProofs, outputRegularBoxesData, fee, timestamp);
        this.outputTokenBoxData = outputTokenBoxData;
    }

    // Specify the unique custom transaction id.
    @Override
    public byte transactionTypeId() { return CreateTokensTransactionId.id(); }

    // Override newBoxes to contains regularBoxes from the parent class appended with CarBox entry.
    // The nonce calculation algorithm for CarBox is the same as in parent class.
    @Override
    public synchronized List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>(super.newBoxes());
            for (int i=0; i<this.outputTokenBoxData.length; i++) {
                long nonce = getNewBoxNonce(outputTokenBoxData[i].proposition(), newBoxes.size());
                newBoxes.add((NoncedBox) new TokenBox(outputTokenBoxData[i], nonce));
            }
        }
        return Collections.unmodifiableList(newBoxes);
    }

    // Define object serialization, that should serialize both parent class entries and CarBoxData as well
    @Override
    public byte[] bytes() {
        ByteArrayOutputStream inputsIdsStream = new ByteArrayOutputStream();
        for(byte[] id: inputRegularBoxIds)
            inputsIdsStream.write(id, 0, id.length);

        ByteArrayOutputStream outputTokenStream = new ByteArrayOutputStream();
        for(TokenBoxData token: outputTokenBoxData) {
            try {
                outputTokenStream.write(Ints.toByteArray(token.bytes().length));
            } catch (IOException e) {
                e.printStackTrace();
            }
            outputTokenStream.write(token.bytes(), 0, token.bytes().length);
        }

        byte[] inputRegularBoxIdsBytes = inputsIdsStream.toByteArray();

        byte[] inputRegularBoxProofsBytes = regularBoxProofsSerializer.toBytes(inputRegularBoxProofs);

        byte[] outputRegularBoxesDataBytes = regularBoxDataListSerializer.toBytes(outputRegularBoxesData);

        byte[] outputTokenBoxDataBytes = outputTokenStream.toByteArray();

        return Bytes.concat(
                Longs.toByteArray(fee()),                               // 8 bytes
                Longs.toByteArray(timestamp()),                         // 8 bytes
                Ints.toByteArray(inputRegularBoxIdsBytes.length),       // 4 bytes
                inputRegularBoxIdsBytes,                                // depends on previous value (>=4 bytes)
                Ints.toByteArray(inputRegularBoxProofsBytes.length),    // 4 bytes
                inputRegularBoxProofsBytes,                             // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputRegularBoxesDataBytes.length),   // 4 bytes
                outputRegularBoxesDataBytes,                            // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputTokenBoxData.length),         // 4 bytes
                outputTokenBoxDataBytes                                   // depends on previous value (>=4 bytes)
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static CreateTokensTransaction parseBytes(byte[] bytes) {
        int offset = 0;

        long fee = BytesUtils.getLong(bytes, offset);
        offset += 8;

        long timestamp = BytesUtils.getLong(bytes, offset);
        offset += 8;

        int batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        ArrayList<byte[]> inputRegularBoxIds = new ArrayList<>();
        int idLength = NodeViewModifier$.MODULE$.ModifierIdSize();
        while(batchSize > 0) {
            inputRegularBoxIds.add(Arrays.copyOfRange(bytes, offset, offset + idLength));
            offset += idLength;
            batchSize -= idLength;
        }

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<Signature25519> inputRegularBoxProofs = regularBoxProofsSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        List<RegularBoxData> outputRegularBoxesData = regularBoxDataListSerializer.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));
        offset += batchSize;

        int before_offset = offset;
        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        TokenBoxData[] outputBoxData = new TokenBoxData[batchSize];
        for (int i = 0; i < batchSize; i++) {
            int size = BytesUtils.getInt(bytes, offset);
            offset += 4;
            outputBoxData[i] = (TokenBoxDataSerializer.getSerializer().parseBytes(Arrays.copyOfRange(bytes, offset, offset + size)));
            offset += size;
        }

        return new CreateTokensTransaction(inputRegularBoxIds, inputRegularBoxProofs, outputRegularBoxesData, outputBoxData, fee, timestamp);
    }

    // Set specific Serializer for CarDeclarationTransaction class.
    @Override
    public TransactionSerializer serializer() {
        return CreateTokensTransactionSerializer.getSerializer();
    }
}
