import sys
import os
sys.path.append(os.getenv("SIDECHAIN_SDK", "") + '/qa/')
from test_framework.util import assert_equal, assert_true, assert_false, fail
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.transaction.findTransactionByID import http_transaction_findById
from httpCalls.tokenApi.createTokens import createTokens
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
This test checks the different rules that limit the tokens creation

Network Configuration:
    1 MC nodes and 1 SC node

Workflow modelled in this test:
    SCNode1: spendForgingStake
    SCNode1: creat ABC tokens
    SCNode1: try create ABC tokens with non-authorized public key
    SCNode1: try create more ABC tokens than the max_limit
    SCNode1: create some other ABC tokens
    SCNode1: create CDE tokens
    SCNode1: try CDE create tokens with non-authorized public key
    SCNode1: try create more CDE tokens than the max_limit
"""
class CreateTokensTest(BasicTest):
    ABC_TYPE = "ABC"
    CDE_TYPE = "CDE"

    def __init__(self):
        #setup network with 2 sidechain nodes
        super(CreateTokensTest, self).__init__(1)

    def run_test(self):
        print "Starting test"
        sc_node1 = self.sc_nodes[0]
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
        publiKeyNode1 = http_wallet_createPrivateKey25519(sc_node1)
        print("PUBKEY \n"+publiKeyNode1)

        #try create 2 new tokens
        print("########## try create 2 new tokens ######### ")
        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 2, publiKeyNode1, 1000)
        assert_true(success)
        self.sc_sync_all()
        self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        #check that the created tokens are present inside the wallet
        boxes = http_wallet_allBoxes(sc_node1)
        (searchBoxFound, carBoxId) = searchBoxListByAttributes(boxes,
                                                'typeId', BOXTYPE_CUSTOM.TOKEN,
                                                'type', self.ABC_TYPE
                                                )
        assert_true(searchBoxFound)
        print("..... OK")

        #try create new tokens with non-authorized publicKey
        print("########## try create new tokens with non-authorized publicKey ######### ")
        new_publiKeyNode1 = http_wallet_createPrivateKey25519(sc_node1)

        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 2, new_publiKeyNode1, 1000)
        assert_false(success)
        self.sc_sync_all()
        print("..... OK")

        #try create more token than max_limit (actual max_limit is 5)
        print("########## try create more token than max_limit (actual max_limit is 5) ######### ")
        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 10, publiKeyNode1, 1000)
        assert_false(success)
        self.sc_sync_all()
        print("..... OK")

        #try create some other token below the token max_limit
        print("########## try create some other token below the token max_limit ######### ")
        (success, transactionid) = createTokens(sc_node1, self.ABC_TYPE, 2, publiKeyNode1, 1000)
        assert_true(success)
        self.sc_sync_all()
        blockhash = self.generateOneBlock(sc_node1)
        self.sc_sync_all()

        boxes = http_wallet_allBoxes(sc_node1)
        boxABC = 0
        for box in boxes:
            if ('type' in box and box['type'] == self.ABC_TYPE):
                boxABC = boxABC +1
        assert_equal(boxABC,4)
        print("..... OK")

        #try create some tokens of another type
        print("########## try create some tokens of another type ######### ")
        (success, transactionid) = createTokens(sc_node1, self.CDE_TYPE, 5, publiKeyNode1, 1000)
        assert_true(success)
        self.sc_sync_all()
        blockhash = self.generateOneBlock(sc_node1)
        self.sc_sync_all()
        print("..... OK")

        #try create new different type tokens with non-authorized publicKey
        print("########## try create new different type tokens with non-authorized publicKey ######### ")
        new_publiKeyNode1 = http_wallet_createPrivateKey25519(sc_node1)

        (success, transactionid) = createTokens(sc_node1, self.CDE_TYPE, 2, new_publiKeyNode1, 1000)
        assert_false(success)
        self.sc_sync_all()
        print("..... OK")

        #try create more token than max_limit (actual max_limit is 7)
        print("########## try create more token than max_limit (actual max_limit is 7) ######### ")
        (success, transactionid) = createTokens(sc_node1, self.CDE_TYPE, 10, publiKeyNode1, 1000)
        assert_false(success)
        self.sc_sync_all()
        print("..... OK")


if __name__ == "__main__":
    CreateTokensTest().main()
