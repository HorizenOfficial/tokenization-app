package io.horizen.tokenization.token.config;

import com.typesafe.config.Config;

import java.util.*;
import java.util.function.Consumer;

/**
 * Memory repsentation of token dictionary defined inside the configuration file
 *
 */
public class TokenDictionary {

    private Map<String, TokenDictionaryItem > items = new HashMap();

    public TokenDictionary(Config config){
        ArrayList<Object> configItems = (ArrayList<Object>) config.getObject("token").get("dictionary").unwrapped();

        configItems.stream().forEach(
                item -> {
            Map mapItem = (Map) item;

            List<String> creatorPropositions =  mapItem.get("creatorPropositions") != null ?(List<String>) mapItem.get("creatorPropositions") : new ArrayList<String>();
            Integer creationPerBlock = mapItem.get("creationPerBlock") != null ?  Integer.valueOf(mapItem.get("creationPerBlock").toString()) : null;

            TokenDictionaryItem ti = new TokenDictionaryItem(
                    (String)mapItem.get("type"),
                    Integer.parseInt(mapItem.get("maxSupply").toString()),
                    TokenDictionaryItem.CreationTYpe.valueOf(mapItem.get("creationType").toString().toUpperCase()),
                    creationPerBlock,
                    creatorPropositions
                    );
            items.put(ti.getType(), ti);

          }
        );
    }

    public Set<String> getAllTypes(){
        return items.keySet();
    }

    public TokenDictionaryItem getItem(String type){
        return items.get(type);
    }

    public int getSize(){
        return items.size();
    }

    public void forEachType(Consumer<TokenDictionaryItem> callback){
        for (String key: items.keySet()){
            TokenDictionaryItem it = items.get(key);
            callback.accept(it);
        }
    }


}
