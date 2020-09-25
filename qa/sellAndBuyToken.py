import sys
import os
sys.path.append(os.getenv("SIDECHAIN_SDK", "") + '/qa/')
from test_framework.util import assert_equal, assert_true, assert_false, fail
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
This test checks a standard workflow: creating a car and selling it to another wallet

Network Configuration:
    1 MC nodes and 2 SC nodes

Workflow modelled in this test:
    SCNode1: spendForgingStake
    SCNode1: createCar
    SCNode2: createPrivateKey25519  of UserB (buyer)
    SCNode1: createCarSellOrder to the UserB
    SCNode2: acceptCarSellOrder
"""
class SellAndBuyTokensTest(BasicTest):
    ABC_TYPE = "ABC"
    CDE_TYPE = "CDE"

    def __init__(self):
        #setup network with 2 sidechain nodes
        super(SellAndBuyTokensTest, self).__init__(2)

    def run_test(self):
        print "Starting test"
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        self.sc_sync_all()

        #convert initial forging amount to standard coinbox and returns the public key owning it and the amount
        (publicKey, convertedForgingStakeValue) = self.convertInitialForging()
        self.sc_sync_all()
        print(str(self.generateOneBlock(sc_node1)))
        self.sc_sync_all()

        #check that the stadard coinbox is present inside the wallet
        boxes = http_wallet_allBoxes(sc_node1)
        (searchBoxFound, boxId)  = searchBoxListByAttributes(boxes,
                                               'typeId', BOXTYPE_STANDARD.REGULAR,
                                               'value', convertedForgingStakeValue,
                                               )
        assert_true(searchBoxFound)

        #gnerate the publicKey able to create tokens
        publicKeyNode1 = http_wallet_createPrivateKey25519(sc_node1)
        print("PUBKEY \n"+publicKeyNode1)

        #Create 1 new token
        print("########## Create 1 new token ######### ")
        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 1, publicKeyNode1, 1000)
        assert_true(success)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the created tokens are present inside the wallet
        boxes = http_wallet_allBoxes(sc_node1)
        (searchBoxFound, tokenBoxId) = searchBoxListByAttributes(boxes,
                                                'typeId', BOXTYPE_CUSTOM.TOKEN,
                                                'type', self.ABC_TYPE
                                                )
        assert_true(searchBoxFound)
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

        orderTxId = createTokenSellOrder(sc_node1, tokenBoxId, publicKeyNode2, 10000000, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the transaction was included in the last block
        bestBlock = http_block_best(sc_node1)
        (searchFound, foundTx) = searchTransactionInBlock(bestBlock, orderTxId)
        assert_true(searchFound)

        #check that the sell order was created correctly
        (searchBoxFound, sellOrderBoxId) = searchBoxListByAttributes(foundTx['newBoxes'],
                    'typeId', BOXTYPE_CUSTOM.SELL_ORDER,
                    'type', self.ABC_TYPE,
                    )
        assert_true(searchBoxFound)

        print("..... OK")

        #Buyer accept the order and invoke AcceptTokenTransaction
        print("########## Buyer accept the order and invoke AcceptTokenTransaction ######### ")

        acceptTokenSellOrder(sc_node2, sellOrderBoxId, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check the user on sidechain node2 has spent money for the car
        assert_equal(http_wallet_balance(sc_node2), 50000000-10000000-1000)

        #user on sidechain node 2 now should own the car
        boxes = http_wallet_allBoxes(sc_node2)
        (searchBoxFound, boughtBoxId)  = searchBoxListByAttributes(boxes,
                                                'typeId', BOXTYPE_CUSTOM.TOKEN,
                                                'type', self.ABC_TYPE,
                                                )
        assert_true(searchBoxFound)

        #user on sidechain node 1 (the seller) shold not own the token anymore
        boxes = http_wallet_allBoxes(sc_node1)
        (searchBoxFound, noBoxId)  = searchBoxListByAttributes(boxes,
                                              'typeId',  BOXTYPE_CUSTOM.TOKEN,
                                              'type', self.ABC_TYPE
                                              )
        assert_false(noBoxId)

        print("..... OK")

        #User on sidechain node 2 resell the token
        print("########## User on sidechain node 2 resell the token ######### ")
        print("BOXID: "+boughtBoxId)
        orderTxId = createTokenSellOrder(sc_node2, boughtBoxId, publicKeyNode1, 10000000, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the transaction was included in the last block
        bestBlock = http_block_best(sc_node2)
        (searchFound, foundTx) = searchTransactionInBlock(bestBlock, orderTxId)
        assert_true(searchFound)

        #check that the sell order was created correctly
        (searchBoxFound, sellOrderBoxId) = searchBoxListByAttributes(foundTx['newBoxes'],
                    'typeId', BOXTYPE_CUSTOM.SELL_ORDER,
                    'type', self.ABC_TYPE,
                    )
        assert_true(searchBoxFound)

        #User on sidechain node 2 cancel the token sell order invoking CancelTokenSellOrder
        print("########## User on sidechain node 2 cancel the token sell order invoking CancelTokenSellOrder ######### ")

        cancelTokenSellOrder(sc_node2, sellOrderBoxId, 1000)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #user on sidechain node 2 now should own the car
        boxes = http_wallet_allBoxes(sc_node2)
        (searchBoxFound, boughtBoxId)  = searchBoxListByAttributes(boxes,
                                                'typeId', BOXTYPE_CUSTOM.TOKEN,
                                                'type', self.ABC_TYPE,
                                                )
        assert_true(searchBoxFound)

        #user on sidechain node 1 (the seller) shold not own the token anymore
        boxes = http_wallet_allBoxes(sc_node1)
        (searchBoxFound, boughtBoxId)  = searchBoxListByAttributes(boxes,
                                              'typeId',  BOXTYPE_CUSTOM.TOKEN,
                                              'type', self.ABC_TYPE
                                              )
        assert_false(searchBoxFound)

        print("..... OK")

if __name__ == "__main__":
    SellAndBuyTokensTest().main()
