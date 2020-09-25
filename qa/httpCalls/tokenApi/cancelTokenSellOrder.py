import json

#create and send a custom transaction tokenApi/cancelTokenSellOrder
def cancelTokenSellOrder(sidechainNode, tokenSellOrderId, fee):
      j = {\
           "tokenSellOrderId": tokenSellOrderId,\
           "fee": fee \
      }
      request = json.dumps(j)
      response = sidechainNode.tokenApi_cancelTokenSellOrder(request)
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


