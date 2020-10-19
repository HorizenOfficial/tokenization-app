package io.horizen.tokenization.token.transaction;

import com.horizen.transaction.DefaultTransactionIncompatibilityChecker;

/**
 * We define a custom IncompatibilityChecker to disallow ForgeTokensTransactions  to be included in mempool (since
 * they can be created only by the forger of a new block)
 */
public class ForgeTokensTransactionIncompatibilityChecker extends DefaultTransactionIncompatibilityChecker {

    @Override
    public boolean isMemoryPoolCompatible() {
        return false;
    }
}
