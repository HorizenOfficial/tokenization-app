import sys
import os
sys.path.append(os.getenv("SIDECHAIN_SDK", "") + '/qa/')
from test_framework.util import assert_equal, assert_true, assert_false, fail, forward_transfer_to_sidechain
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.tokenApi.createTokens import createTokens
from httpCalls.tokenApi.createTokenSellOrder import createTokenSellOrder
from httpCalls.tokenApi.acceptTokenSellOrder import acceptTokenSellOrder
from httpCalls.tokenApi.cancelTokenSellOrder import cancelTokenSellOrder
from httpCalls.block.best import http_block_best
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.wallet.createPrivateKey25519 import  http_wallet_createPrivateKey25519
from utils.searchBoxListByAttributes import searchBoxListByAttributes
from utils.searchTransactionInBlock import searchTransactionInBlock
from basicTest import BasicTest
from resources.testdata import BOXTYPE_STANDARD, BOXTYPE_CUSTOM

"""
This test checks the creation of multiple tokens and a sell order containing more than one token

Network Configuration:
    1 MC nodes and 2 SC nodes

Workflow modelled in this test:
    McNode: send some money to SCNode1 (forward transfer)
    SCNode1: createToken (with number of token = 3)
    SCNode2: createPrivateKey25519  of UserB (buyer)
    SCNode1: createTokenSellOrder to the UserB of 2 of the previous tokens
    SCNode2: acceptTokenSellOrder
        check that  both the tokens now are owned by UserB
        check that  the third tokens is still owned by UserA
    SCNode2: createTokenSellOrder to the UserA
    SCNode1: cancelTokenSellOrder 
        check that the both the tokens are still owned by UserB
"""
class MultipleTokensTest(BasicTest):
    ABC_TYPE = "ABC"
    CDE_TYPE = "CDE"

    def __init__(self):
        #setup network with 2 sidechain nodes
        super(MultipleTokensTest, self).__init__(2)

    def run_test(self):
        print "Starting test"
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        publicKeySeller = self.sc_nodes_bootstrap_info.genesis_account.publicKey
        self.sc_sync_all()

        #we need regular coins (the genesis account balance is locked into forging stake), so we perform a
        #forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      publicKeySeller,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the wallet balance is doubled now (forging stake + the forward transfer) (we need to convert to zentoshi also)
        assert_equal(http_wallet_balance(sc_node1),  (self.sc_nodes_bootstrap_info.genesis_account_balance * 2) * 100000000)

        #gnerate the publicKey able to create tokens
        publicKeyNode1 = http_wallet_createPrivateKey25519(sc_node1)
        print("PUBKEY \n"+publicKeyNode1)

        #Create 1 new token
        print("########## Create 1 new token ######### ")
        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 3, publicKeyNode1, 1000)
        assert_true(success)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the created tokens are present inside the wallet (and collect their tokenIds and boxid)
        boxes = http_wallet_allBoxes(sc_node1)

        tokenIdList = []
        tokenBoxIdList = []
        for box in boxes:
            if (("typeId" in box and box["typeId"] == BOXTYPE_CUSTOM.TOKEN) and
                ("type" in box and box["type"] == self.ABC_TYPE)):
                    tokenIdList.append(box["tokenId"])
                    tokenBoxIdList.append(box["id"])
        assert_true(len(tokenIdList) == 3)
        print("..... OK")

        #Create new sellOrderTransaction
        print("########## Create new sellOrderTransaction ######### ")

        #Node 2 create new publicKey
        publicKeyNode2 = http_wallet_createPrivateKey25519(sc_node2)

        #send some coin to the user on sidechain node 2
        sendCoinsToAddress(sc_node1, publicKeyNode2, 50000000, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check the user on sidechain node2 has received the money
        assert_equal(http_wallet_balance(sc_node2), 50000000)

        orderTxId = createTokenSellOrder(sc_node1, [tokenBoxIdList[0], tokenBoxIdList[1]], publicKeyNode2, 10000000, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the transaction was included in the last block
        bestBlock = http_block_best(sc_node1)
        (searchFound, foundTx) = searchTransactionInBlock(bestBlock, orderTxId)
        assert_true(searchFound)

        #check that the sell order was created correctly
        (searchBoxFound, sellOrderBoxId) = searchBoxListByAttributes(foundTx['newBoxes'],
                    'typeId', BOXTYPE_CUSTOM.SELL_ORDER
                    )
        assert_true(searchBoxFound)

        print("..... OK")

        #Buyer accept the order and invoke AcceptTokenTransaction
        print("########## Buyer accept the order and invoke AcceptTokenTransaction ######### ")

        acceptTokenSellOrder(sc_node2, sellOrderBoxId, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check the user on sidechain node2 has spent money for the order
        assert_equal(http_wallet_balance(sc_node2), 50000000-10000000-1000)

        #user on sidechain node 2 now should own the first 2 tokens
        boxes = http_wallet_allBoxes(sc_node2)
        userBtokenIdList = []
        userBtokenBoxIdList = []
        for box in boxes:
            if (("typeId" in box and box["typeId"] == BOXTYPE_CUSTOM.TOKEN) and
                    ("type" in box and box["type"] == self.ABC_TYPE)):
                userBtokenIdList.append(box["tokenId"])
                userBtokenBoxIdList.append(box["id"])
        assert_true(len(userBtokenIdList) == 2)

        #user on sidechain node 1 (the seller) shold own the third token
        boxes = http_wallet_allBoxes(sc_node1)
        userAtokenIdList = []
        userAtokenBoxIdList = []
        for box in boxes:
            if (("typeId" in box and box["typeId"] == BOXTYPE_CUSTOM.TOKEN) and
                    ("type" in box and box["type"] == self.ABC_TYPE)):
                userAtokenIdList.append(box["tokenId"])
                userAtokenBoxIdList.append(box["id"])
        assert_true(len(userAtokenIdList) == 1)
        assert_true(userAtokenIdList[0] == tokenIdList[2])

        print("..... OK")

        #User on sidechain node 2 resell the token
        print("########## User on sidechain node 2 resell the tokens ######### ")
        orderTxId = createTokenSellOrder(sc_node2,  [userBtokenBoxIdList[0], userBtokenBoxIdList[1]], publicKeyNode1, 10000000, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the transaction was included in the last block
        bestBlock = http_block_best(sc_node2)
        (searchFound, foundTx) = searchTransactionInBlock(bestBlock, orderTxId)
        assert_true(searchFound)

        #check that the sell order was created correctly
        (searchBoxFound, sellOrderBoxId) = searchBoxListByAttributes(foundTx['newBoxes'],
                    'typeId', BOXTYPE_CUSTOM.SELL_ORDER
                    )
        assert_true(searchBoxFound)

        #User on sidechain node 2 cancel the token sell order invoking CancelTokenSellOrder
        print("########## User on sidechain node 2 cancel the token sell order invoking CancelTokenSellOrder ######### ")

        cancelTokenSellOrder(sc_node2, sellOrderBoxId, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #user on sidechain node 2 should still own the tokens
        boxes = http_wallet_allBoxes(sc_node2)
        userBtokenIdListBis = []
        userBtokenBoxIdListBis = []
        for box in boxes:
            if (("typeId" in box and box["typeId"] == BOXTYPE_CUSTOM.TOKEN) and
                    ("type" in box and box["type"] == self.ABC_TYPE)):
                userBtokenIdListBis.append(box["tokenId"])
                userBtokenBoxIdListBis.append(box["id"])
        assert_true(len(userBtokenIdListBis) == 2)

        #tokenIds should not have been changed
        token1IdFound = False
        token2IdFound = False
        token3IdFound = False
        for currId in userBtokenIdListBis:
            if (currId == tokenIdList[0]):
                token1IdFound = True
            if (currId == tokenIdList[1]):
                token2IdFound = True
            if (currId == tokenIdList[2]):
                token3IdFound = True
        assert_true(token1IdFound)
        assert_true(token2IdFound)
        assert_false(token3IdFound)

        print("..... OK")

if __name__ == "__main__":
    MultipleTokensTest().main()
