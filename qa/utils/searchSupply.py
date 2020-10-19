"""
search inside a tokenApi/supply response the forged counter for a specific token type
"""
def searchSupply(supplyTesponse, tokenType):
    for ele in supplyTesponse:
        if (ele["tokenType"] == tokenType):
                return ele["forged"]
    return 0

