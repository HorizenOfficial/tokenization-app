import json

#executes a  transaction/findById call
def http_block_findById(sidechainNode, blockId):
      j = {\
            "blockId": blockId
      }
      request = json.dumps(j)
      response = sidechainNode.block_findById(request)
      return response["result"]



