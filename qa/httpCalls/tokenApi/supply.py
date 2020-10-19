import json

#create and send a custom transaction tokenApi/createTokenSellOrder
def supply(sidechainNode):
    response = sidechainNode.tokenApi_supply()
    print("Supply RESPONSE\n"+str(response))
    if ("error" in response):
        return (False)
    else:
        return response["result"]["supply"]