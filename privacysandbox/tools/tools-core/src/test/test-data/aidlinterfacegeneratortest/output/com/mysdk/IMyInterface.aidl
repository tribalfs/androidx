package com.mysdk;

import com.mysdk.IMyInterface;
import com.mysdk.IMyInterfaceTransactionCallback;

oneway interface IMyInterface {
    void methodWithInterfaceParam(IMyInterface myInterface);
    void suspendMethodWithInterfaceReturn(IMyInterfaceTransactionCallback transactionCallback);
}