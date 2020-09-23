package io.horizen.tokenization.token.transaction;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.horizen.box.BoxUnlocker;
import com.horizen.box.NoncedBox;
import com.horizen.box.data.RegularBoxData;
import io.horizen.tokenization.token.box.TokenSellOrderBox;
import com.horizen.proof.Proof;
import com.horizen.proof.Signature25519;
import com.horizen.proposition.Proposition;
import com.horizen.transaction.TransactionSerializer;
import com.horizen.utils.BytesUtils;
import io.horizen.tokenization.token.info.TokenSellOrderInfo;
import scorex.core.NodeViewModifier$;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.horizen.tokenization.token.transaction.TokenTransactionsIdsEnum.SellTokenTransactionId;

// SellCarTransaction is nested from AbstractRegularTransaction so support regular coins transmission as well.
// SellCarTransaction was designed to create a SellOrder for a specific buyer for given CarBox owned by the user.
// As outputs it contains possible RegularBoxes(to pay fee and make change) and new CarSellOrder entry.
// As unlockers it contains RegularBoxes and CarBox to open.
public final class SellTokenTransaction extends AbstractRegularTransaction {

    // CarSellOrderInfo is a view that describes what car box to open and what is the sell order(car attributes, price and buyer info).
    // But inside it contains just a minimum set of info (like CarBox itself and price) that is the unique source of data.
    // So, no one outside controls what will be the specific outputs of this transaction.
    // Any malicious actions will lead to transaction invalidation.
    // For example, if CarBox was opened, the CarSellOrder obliged to contains the same car attributes and owner info.
    private final TokenSellOrderInfo tokenSellOrderInfo;

    private List<NoncedBox<Proposition>> newBoxes;

    public SellTokenTransaction(List<byte[]> inputRegularBoxIds,
                              List<Signature25519> inputRegularBoxProofs,
                              List<RegularBoxData> outputRegularBoxesData,
                              TokenSellOrderInfo tokenSellOrderInfo,
                              long fee,
                              long timestamp) {
        super(inputRegularBoxIds, inputRegularBoxProofs, outputRegularBoxesData, fee, timestamp);
        this.tokenSellOrderInfo = tokenSellOrderInfo;
    }

    // Specify the unique custom transaction id.
    @Override
    public byte transactionTypeId() {
        return SellTokenTransactionId.id();
    }

    // Override unlockers to contains regularBoxes from the parent class appended with CarBox entry to be opened.
    @Override
    public List<BoxUnlocker<Proposition>> unlockers() {
        // Get Regular unlockers from base class.
        List<BoxUnlocker<Proposition>> unlockers = super.unlockers();

        BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
            @Override
            public byte[] closedBoxId() {
                return tokenSellOrderInfo.getTokenBoxToOpen().id();
            }

            @Override
            public Proof boxKey() {
                return tokenSellOrderInfo.getCarBoxSpendingProof();
            }
        };
        // Append with the CarBox unlocker entry.
        unlockers.add(unlocker);

        return unlockers;
    }

    // Override newBoxes to contains regularBoxes from the parent class appended with CarSellOrderBox and payment entries.
    // The nonce calculation algorithm for CarSellOrderBox is the same as in parent class.
    @Override
    public List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>(super.newBoxes());
            long nonce = getNewBoxNonce(tokenSellOrderInfo.getSellOrderBoxData().proposition(), newBoxes.size());
            // Here we enforce output CarSellOrder data calculation.
            // Any malicious action will lead to different inconsistent data to the honest nodes State.
            newBoxes.add((NoncedBox) new TokenSellOrderBox(tokenSellOrderInfo.getSellOrderBoxData(), nonce));

        }
        return Collections.unmodifiableList(newBoxes);
    }

    // Define object serialization, that should serialize both parent class entries and CarSellOrderInfo as well
    @Override
    public byte[] bytes() {
        ByteArrayOutputStream inputsIdsStream = new ByteArrayOutputStream();
        for(byte[] id: inputRegularBoxIds)
            inputsIdsStream.write(id, 0, id.length);

        byte[] inputRegularBoxIdsBytes = inputsIdsStream.toByteArray();

        byte[] inputRegularBoxProofsBytes = regularBoxProofsSerializer.toBytes(inputRegularBoxProofs);

        byte[] outputRegularBoxesDataBytes = regularBoxDataListSerializer.toBytes(outputRegularBoxesData);

        byte[] tokenSellOrderInfoBytes = tokenSellOrderInfo.bytes();

        return Bytes.concat(
                Longs.toByteArray(fee()),                               // 8 bytes
                Longs.toByteArray(timestamp()),                         // 8 bytes
                Ints.toByteArray(inputRegularBoxIdsBytes.length),       // 4 bytes
                inputRegularBoxIdsBytes,                                // depends on previous value (>=4 bytes)
                Ints.toByteArray(inputRegularBoxProofsBytes.length),    // 4 bytes
                inputRegularBoxProofsBytes,                             // depends on previous value (>=4 bytes)
                Ints.toByteArray(outputRegularBoxesDataBytes.length),   // 4 bytes
                outputRegularBoxesDataBytes,                            // depends on previous value (>=4 bytes)
                Ints.toByteArray(tokenSellOrderInfoBytes.length),         // 4 bytes
                tokenSellOrderInfoBytes                                   // depends on previous value (>=4 bytes)
        );
    }

    // Define object deserialization similar to 'toBytes()' representation.
    public static SellTokenTransaction parseBytes(byte[] bytes) {
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

        batchSize = BytesUtils.getInt(bytes, offset);
        offset += 4;

        TokenSellOrderInfo tokenSellOrderInfo = TokenSellOrderInfo.parseBytes(Arrays.copyOfRange(bytes, offset, offset + batchSize));

        return new SellTokenTransaction(inputRegularBoxIds, inputRegularBoxProofs, outputRegularBoxesData, tokenSellOrderInfo, fee, timestamp);
    }

    // Set specific Serializer for SellCarTransaction class.
    @Override
    public TransactionSerializer serializer() {
        return SellTokenTransactionSerializer.getSerializer();
    }
}
