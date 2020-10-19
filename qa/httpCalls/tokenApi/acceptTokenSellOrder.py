import json

#create and send a custom transaction tokenApi/acceptTokenSellOrder
def acceptTokenSellOrder(sidechainNode, tokenSellOrderId, fee):
      j = {\
           "tokenSellOrderId": tokenSellOrderId,\
           "fee": fee \
      }
      request = json.dumps(j)
      response = sidechainNode.tokenApi_acceptTokenSellOrder(request)
      if ("error" in response):
          return (False, None)
      else:
          transactionBytes = response["result"]["transactionBytes"]
          j = {\
               "transactionBytes": transactionBytes,\
          }
          request = json.dumps(j)
          response = sidechainNode.transaction_sendTransaction(request)
          if ("error" in response):
                return (False, None)
          else:
                return response["result"]["transactionId"]


