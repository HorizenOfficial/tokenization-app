**Tokenization-app Application**

"Tokenization-app" is a demo blockchain application based on the Beta version of the [Sidechains SDK by Horizen](https://github.com/HorizenOfficial/Sidechains-SDK). It supports all Sidechains SDK core features, and it also introduces some custom data and logic.

Tokenization-app is an example of how the Sidechains SDK can be extended to support tokenization functionalities. It shows in particular:
* How to introduce custom Transactions, Boxes, Proofs and Propositions.
* How to define custom API.
* How to manage custom types and API endpoints.

**Tokenization custom logic**

A user of the Tokenization-app application can:
* Create tokens by specifying the token type.
* Sell an owned token by creating a the sell order associated to a price, that will have to be accepted by a specified buyer.
* Cancel the token selling process, i.e. the token owner can revert the sell order.
* Accept a token sell order, with a payment by the specified buyer to the previous token owner. This transaction makes the buyer the new owner of the token.

**Supported platforms**

The Tokenization-app application is available and tested on Linux and Windows (64bit).

**Requirements**

* Java 8 or newer (Java 11 recommended)
* Maven

**Bootstrap and run**

The process to set up a Lambo sidechain network and run its nodes with a connection to the mainchain is identical to the one described in [Sidechains-SDK simple app example](https://github.com/HorizenOfficial/Sidechains-SDK/blob/master/examples/simpleapp/mc_sc_workflow_example.md). There is no custom steps specific to Lambo application.

For initial testing on regtest, you can use the predefined [tokenization_settings.conf](src/main/resources/tokenization_settings.conf) configuration file.

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

Each node has an API server bound to the `address:port` specified in its configuration file. You can use any HTTP client that supports POST requests, e.g. Curl or Postman.

To do all the operations described in the "Lambo custom logic" chapter of the SDK tutorial (link), you can use the following API endpoints (curl examples provided):

* To create a new token:
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
    "tokenBoxId": "2b05167dbae0f6f6cb2a6c09cc9fbdc450c93b2a12c6b7847a3119f3a1779b09",
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
* To purchase a car that is on sale:
```
curl --location --request POST '127.0.0.1:9085/tokenApi/acceptTokenSellOrder' \
--header 'Content-Type: application/json' \
--data-raw '{
    "tokenSellOrderId": "408573c37b96a7e292ffd66eb62a5a53e98a5e6d91c4a93b1784997f3d7e6c7e",
    "fee": 100
}'
```



