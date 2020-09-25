import json
#create and send a custom transaction tokenApi/createTokens
#returns (<success>, <transactionid>)
def createTokens(sidechainNode, type, numberOfTokens, proposition, fee):
      j = {\
           "type": type,\
           "numberOfTokens": numberOfTokens,\
           "proposition": proposition, \
           "fee": fee \
      }
      request = json.dumps(j)
      response = sidechainNode.tokenApi_createTokens(request)
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
            return (True, response["result"]["transactionId"])


