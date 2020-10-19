#!/usr/bin/env python2
import sys
import os
sys.path.append(os.getenv("SIDECHAIN_SDK", "") + '/qa/')
from test_framework.util import assert_equal, assert_true, assert_false, fail
from t1_createTokens import CreateTokensTest
from t2_sellAndBuyToken import SellAndBuyTokensTest
from t3_multipleTokens import MultipleTokensTest


def run_test(test):
    try:

        test.main()
    except SystemExit as e:
        return e.code
    return 0

def run_tests(log_file):
    print "Running all tests "
    original = sys.stdout
    sys.stdout = log_file

    result = run_test(CreateTokensTest())
    assert_equal(0, result, "CreateTokensTest test failed!")

    result = run_test(SellAndBuyTokensTest())
    assert_equal(0, result, "SellAndBuyTokensTest test failed!")

    result = run_test(MultipleTokensTest())
    assert_equal(0, result, "MultipleTokensTest test failed!")

    sys.stdout = original
    print "Test suite completed succesfully! - see log file: " + log_file.name

if __name__ == "__main__":
    log_file = open("sc_test.log", "w")
    run_tests(log_file)
