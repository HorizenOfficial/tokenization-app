**Tokenization-app Application**

"Tokenization-app" is a demo blockchain application based on the Beta version of the [Sidechains SDK by Horizen](https://github.com/HorizenOfficial/Sidechains-SDK). It supports all Sidechains SDK core features, and it also introduces some custom data and logic.

Tokenization-app is an example of how the Sidechains SDK can be extended to support tokenization functionalities. 
It shows in particular:
* How to introduce custom Transactions, Boxes, Proofs and Propositions.
* How to define custom API.
* How to manage custom types and API endpoints.
* How to add an application specific configuration
* How to extend the forger logic to include some auto-generated transactions

**Tokenization custom logic**

A user of the Tokenization-app application can:
* Create tokens by specifying the token type and the number of tokens to create. 
The app is designed to allow only some predefined users to forge the tokens, and to enforce a maximum amount of tokens 
to be forged for each token type. 
The parameters for these rules are defined in custom section of the configuration file (see below).
* Sell one or more owned tokens, by creating a sell order that will have to be accepted by a specified buyer.
Each sell order has a price and encloses one or more token.
* Cancel the token selling process, i.e. the creator of a sell order can delete it.
* Accept a token sell order, with a payment by the specified buyer to the previous token owner. 
This transaction makes the buyer the new owner of all the tokens contained in the sell order 
(is not poossible to acccept partially a sell order).

Moreover, the application will track the total amount of token forged and expose an endpoint that shows this value
for each token type.

**Supported platforms**

The Tokenization-app application is available and tested on Linux and Windows (64bit).

**Requirements**

* Java 8 or newer (Java 11 recommended)
* Maven

**Configuration**
For initial testing on regtest, you can use the predefined [tokenization_settings.conf](src/main/resources/tokenization_settings.conf) configuration file.
The bottom part of the configuration file contains some specific parameters used by this application:

token {
    dictionary = [
        {
            type =  "ABC"
            maxSupply = 10       
     	    creationType = "auto"
	        creationPerBlock = 2
        },
        {
            type =  "CDE"
            maxSupply = 7  
	        creationType = "manual"
	        creatorPropositions = [
	          "4a2437a1106a9b5912dfc2162180fe5758cfe449f45c72bfdee7ad928a570c0e",
	          "af50c5bb2ee3dbead9828f35b12224acdad89d9cf389ad269bed9761f271c045"
	        ]
        }    
    ]
}

Each element of the config property token.dictionary describes a type of token exchanged in our sidechain, 
with the following properties:
- type: string unique identifier of the token
- maxSupply: total overall number of tokens that can be created
- creationType: can have the following values:
    - “manual”: new tokens must be created manually with a createTokenTransaction
    - “auto”:  new tokens are created automatically at every forged block. The tokens created are locked with  the same public key of the block forger. For tokens type having this “auto” property set, the  manual createTokenTransaction cannot be invoked.
- creationPerBlock: if creationType=”auto”, this property specifies the amount of tokens that are generated on each block, until the maxSupply value is reached
- creatorPropositions: if creationType=”manual”, this property specifies the list of publickeys that are allowed to execute a createTokenTransaction, until the maxSupply value is reached. New tokens generated are  locked with  the public key of the creator that executes the transaction.

  
**Bootstrap and run**

The process to set up a sidechain network and run its nodes with a connection to the mainchain is identical to the one 
described in [Sidechains-SDK simple app example](https://github.com/HorizenOfficial/Sidechains-SDK/blob/master/examples/simpleapp/mc_sc_workflow_example.md). There is no custom steps specific to Lambo application.

Then to run a Tokenization node, you can:
1. Go to the project root folder.
2. Build and package Tokenization jar: `mvn package`.
3. Execute the application with the following command:

For Linux: 
```
java -cp ./target/tokenization-app-0.1.0.jar:./target/lib/* io.horizen.tokenization.TokenApp ./src/main/resources/tokenization_settings.conf
```

For Windows:
```
java -cp ./target/tokenization-app-0.1.0.jar;./target/lib/* io.horizen.tokenization.TokenApp ./src/main/resources/tokenization_settings.conf
```



**Interaction**

Each node has an API server bound to the `address:port` specified in its configuration file.
 You can use any HTTP client that supports POST requests, e.g. Curl or Postman.
You can use the following API endpoints (curl examples provided):

* To create new tokens of a specific type:
```
curl --location --request POST '127.0.0.1:9085/tokenApi/createTokens' \
--header 'Content-Type: application/json' \
--data-raw '{
    "type": "ABCDE",
    "numberOfTokens": 3,
    "proposition": "a5b10622d70f094b7276e04608d97c7c699c8700164f78e16fe5e8082f4bb2ac",
    "fee": 100
}'
```

* To create a sell order:
```
curl --location --request POST '127.0.0.1:9085/tokenApi/createTokenSellOrder' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tokenBoxIds": ["tokenBoxId1", "tokenBoxId2"...],
    "sellPrice": 10000000000,
    "buyerProposition": "3368c35a21d9edef9a643dbd4fce0d7fa8c8bf4e556bd449780d9926e4f09689",
    "fee": 100
}'
```
* To cancel a sell order (by the owner):
```
curl --location --request POST '127.0.0.1:9085/tokenApi/cancelTokenSellOrder' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tokenSellOrderId": "21639caab0743478ee6ebf06dd0070f6ab0faef4b8511631a9cd517b88d6e853",
    "fee": 100
}'
```  
* To accept a sell order (by the buyer):
```
curl --location --request POST '127.0.0.1:9085/tokenApi/acceptTokenSellOrder' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tokenSellOrderId": "408573c37b96a7e292ffd66eb62a5a53e98a5e6d91c4a93b1784997f3d7e6c7e",
    "fee": 100
}'
```

* To see the total supply (number of tokens forged and total number of tokens that can be forged)
```
curl --location --request POST '127.0.0.1:9085/tokenApi/supply' \
--header 'Content-Type: application/json' \
```




