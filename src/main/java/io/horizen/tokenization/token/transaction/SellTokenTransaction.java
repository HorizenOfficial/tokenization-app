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

// SellTokenTransaction is nested from AbstractRegularTransaction so support regular coins transmission as well.
// SellTokenTransaction was designed to create a SellOrder for a specific buyer for given TokenBox owned by the user.
// As outputs it contains possible RegularBoxes(to pay fee and make change) and new TokenSellOrder entry.
// As unlockers it contains RegularBoxes and TokenBox to open.
public final class SellTokenTransaction extends AbstractRegularTransaction {

    // TokenSellOrderInfo is a view that describes what car box to open and what is the sell order(car attributes, price and buyer info).
    // But inside it contains just a minimum set of info (like TokenBox itself and price) that is the unique source of data.
    // So, no one outside controls what will be the specific outputs of this transaction.
    // Any malicious actions will lead to transaction invalidation.
    // For example, if TokenBox was opened, the TokenSellOrder obliged to contains the same token attributes and owner info.
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

    // Override unlockers to contains regularBoxes from the parent class appended with TokenBox entry to be opened.
    @Override
    public List<BoxUnlocker<Proposition>> unlockers() {
        // Get Regular unlockers from base class.
        List<BoxUnlocker<Proposition>> unlockers = super.unlockers();

        for (int i = 0 ; i < tokenSellOrderInfo.getTotalTokensToSell(); i++) {
            final byte[] boxId = tokenSellOrderInfo.getTokenBoxToOpen(i).id();
            final Proof proof = tokenSellOrderInfo.getTokenBoxSpendingProof(i);
            BoxUnlocker<Proposition> unlocker = new BoxUnlocker<Proposition>() {
                @Override
                public byte[] closedBoxId() {
                    return boxId;
                }
                @Override
                public Proof boxKey() {
                    return proof;
                }
            };
            // Append with the TokenBox unlocker entry.
            unlockers.add(unlocker);
        }

        return unlockers;
    }

    // Override newBoxes to contains regularBoxes from the parent class appended with TokenSellOrderBox and payment entries.
    @Override
    public List<NoncedBox<Proposition>> newBoxes() {
        if(newBoxes == null) {
            newBoxes = new ArrayList<>(super.newBoxes());
            long nonce = getNewBoxNonce(tokenSellOrderInfo.getSellOrderBoxData().proposition(), newBoxes.size());
            // Here we enforce output TokenSellOrder data calculation.
            // Any malicious action will lead to different inconsistent data to the honest nodes State.
            newBoxes.add((NoncedBox) new TokenSellOrderBox(tokenSellOrderInfo.getSellOrderBoxData(), nonce));

        }
        return Collections.unmodifiableList(newBoxes);
    }

    // Define object serialization, that should serialize both parent class entries and TokenSellOrderInfo as well
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

    // Set specific Serializer for SellTokenTransaction class.
    @Override
    public TransactionSerializer serializer() {
        return SellTokenTransactionSerializer.getSerializer();
    }
}
