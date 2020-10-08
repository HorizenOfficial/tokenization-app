package io.horizen.tokenization.token.config;

import java.util.List;

/**
 * Memory representation ot token dicrionary configuration item
 */
public class TokenDictionaryItem {

    private String type;
    private Integer maxSupply;
    private CreationTYpe creationTYpe;
    private Integer creationPerBlock;
    private List<String> creatorPropositions;


    public enum CreationTYpe {
        AUTO,
        MANUAL,
    }

    public TokenDictionaryItem(String type, Integer maxSupply, CreationTYpe creationType, Integer creationPerBlock, List<String> creatorPropositions){
        this.type = type;
        this.maxSupply = maxSupply;
        this.creationTYpe = creationType;
        this.creatorPropositions = creatorPropositions;
        this.creationPerBlock = creationPerBlock;
    }
    public String getType() {
        return type;
    }

    public Integer getMaxSupply() {
        return maxSupply;
    }

    public Integer getCreationPerBlock() {
        return creationPerBlock;
    }

    public CreationTYpe getCreationTYpe() {
        return creationTYpe;
    }


    public List<String> getCreatorPropositions() {
        return creatorPropositions;
    }
}
