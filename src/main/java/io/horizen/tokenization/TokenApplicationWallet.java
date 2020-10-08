package io.horizen.tokenization;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;
import com.horizen.secret.Secret;
import com.horizen.wallet.ApplicationWallet;
import java.util.List;

// There is no custom logic for this app at the moment
public class TokenApplicationWallet implements ApplicationWallet {

    @Override
    public void onAddSecret(Secret secret) {
    }

    @Override
    public void onRemoveSecret(Proposition proposition) {
    }

    @Override
    public void onChangeBoxes(byte[] version, List<Box<Proposition>> boxesToUpdate, List<byte[]> boxIdsToRemove) {
    }

    @Override
    public void onRollback(byte[] version) {
    }
}