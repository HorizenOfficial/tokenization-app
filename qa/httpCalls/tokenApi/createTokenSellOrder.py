import json

#create and send a custom transaction tokenApi/createTokenSellOrder
def createTokenSellOrder(sidechainNode, tokenBoxId, buyerProposition, sellPrice, fee):
      j = {\
           "tokenBoxId": tokenBoxId,\
           "buyerProposition": buyerProposition,\
           "sellPrice": sellPrice,\
           "fee": fee \
      }
      request = json.dumps(j)
      response = sidechainNode.tokenApi_createTokenSellOrder(request)
      print("Sell order creation RESPONSE\n"+str(response))
      if ("error" in response):
          return (False, None)
      else:
          print("Create sellorder success!")
          transactionBytes = response["result"]["transactionBytes"]
          j = {\
               "transactionBytes": transactionBytes,\
          }
          request = json.dumps(j)
          response = sidechainNode.transaction_sendTransaction(request)
          print("Sell order RESPONSE\n"+str(response))
          if ("error" in response):
                return (False, None)
          else:
                return response["result"]["transactionId"]


