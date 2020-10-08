package io.horizen.tokenization.token.forge;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.horizen.chain.SidechainBlockInfo;
import com.horizen.forge.ApplicationForger;
import com.horizen.proposition.PublicKey25519Proposition;
import com.horizen.transaction.SidechainTransaction;
import io.horizen.tokenization.TokenApp;
import io.horizen.tokenization.token.box.data.TokenBoxData;
import io.horizen.tokenization.token.config.TokenDictionary;
import io.horizen.tokenization.token.config.TokenDictionaryItem;
import io.horizen.tokenization.token.services.IDInfoDBService;
import io.horizen.tokenization.token.transaction.ForgeTokensTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Custom application forger: if we have some tokens defined with auto-forging feature enabled, this calss is used to
 * add at forging time an additional transaction with the new token creation.
 */
public class TokenApplicationForger implements ApplicationForger {

    private static Logger log =  LoggerFactory.getLogger(TokenApp.class);

    IDInfoDBService iDInfoDbService;
    TokenDictionary tokenDictionary;

    @Inject
    public TokenApplicationForger(IDInfoDBService iDInfoDbService,
                                  @Named("TokenDictionary") TokenDictionary tokenDictionary) {
        this.iDInfoDbService = iDInfoDbService;
        this.tokenDictionary = tokenDictionary;
    }

    @Override
    public List<SidechainTransaction> onForge(SidechainBlockInfo parentBlockInfo,  PublicKey25519Proposition blockSignPuplicKey) {

        log.debug("Application onForge");

        ArrayList retArr = new ArrayList();
        List<TokenBoxData> tokensToForge = new ArrayList<>();
        this.tokenDictionary.forEachType(ele -> {
            if (ele.getCreationTYpe() == TokenDictionaryItem.CreationTYpe.AUTO) {
                //the number of token to forge depends on configuration properties getCreationPerBlock and maxSupply
                int remainingTokens = ele.getMaxSupply() - iDInfoDbService.getTypeCount(ele.getType());
                int tokenToForgeInThisBlock = ele.getCreationPerBlock() > remainingTokens ? remainingTokens : ele.getCreationPerBlock();
                if (tokenToForgeInThisBlock > 0){
                    log.debug("Auto-forging of "+tokenToForgeInThisBlock+" tokens of type: "+ele.getType());
                    while (tokenToForgeInThisBlock > 0) {
                        tokensToForge.add( new TokenBoxData(
                                blockSignPuplicKey,
                                iDInfoDbService.generateTokenId(tokenToForgeInThisBlock, Optional.empty()),
                                ele.getType())
                        );
                        tokenToForgeInThisBlock--;
                    }
                }
            }
        });

        if (tokensToForge.size() > 0) {
            long creationTimestamp = System.currentTimeMillis();
            ForgeTokensTransaction ct = new ForgeTokensTransaction(
                    tokensToForge.toArray(new TokenBoxData[tokensToForge.size()]),
                    creationTimestamp
            );
            retArr.add(ct);
        }
        return retArr;
    }
}