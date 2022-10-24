package com.mysdk

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

public class TestSandboxSdkClientProxy(
    public val remote: ITestSandboxSdk,
) : TestSandboxSdk {
    public override suspend fun doSomethingAsync(
        first: Int,
        second: String,
        third: Long,
    ): Boolean = suspendCancellableCoroutine {
        var mCancellationSignal: ICancellationSignal? = null
        val transactionCallback = object: IBooleanTransactionCallback.Stub() {
            override fun onCancellable(cancellationSignal: ICancellationSignal) {
                if (it.isCancelled) {
                    cancellationSignal.cancel()
                }
                mCancellationSignal = cancellationSignal
            }
            override fun onSuccess(result: Boolean) {
                it.resumeWith(Result.success(result))
            }
            override fun onFailure(errorCode: Int, errorMessage: String) {
                it.resumeWithException(RuntimeException(errorMessage))
            }
        }
        remote.doSomethingAsync(first, second, third, transactionCallback)
        it.invokeOnCancellation {
            mCancellationSignal?.cancel()
        }
    }

    public override fun echoBoolean(input: Boolean): Unit {
        remote.echoBoolean(input)
    }

    public override fun echoChar(input: Char): Unit {
        remote.echoChar(input)
    }

    public override fun echoDouble(input: Double): Unit {
        remote.echoDouble(input)
    }

    public override fun echoFloat(input: Float): Unit {
        remote.echoFloat(input)
    }

    public override fun echoInt(input: Int): Unit {
        remote.echoInt(input)
    }

    public override fun echoLong(input: Long): Unit {
        remote.echoLong(input)
    }

    public override fun echoString(input: String): Unit {
        remote.echoString(input)
    }

    public override fun receiveAndReturnNothing(): Unit {
        remote.receiveAndReturnNothing()
    }

    public override suspend fun receiveAndReturnNothingAsync(): Unit = suspendCancellableCoroutine {
        var mCancellationSignal: ICancellationSignal? = null
        val transactionCallback = object: IUnitTransactionCallback.Stub() {
            override fun onCancellable(cancellationSignal: ICancellationSignal) {
                if (it.isCancelled) {
                    cancellationSignal.cancel()
                }
                mCancellationSignal = cancellationSignal
            }
            override fun onSuccess() {
                it.resumeWith(Result.success(Unit))
            }
            override fun onFailure(errorCode: Int, errorMessage: String) {
                it.resumeWithException(RuntimeException(errorMessage))
            }
        }
        remote.receiveAndReturnNothingAsync(transactionCallback)
        it.invokeOnCancellation {
            mCancellationSignal?.cancel()
        }
    }

    public override fun receiveMultipleArguments(
        first: Int,
        second: String,
        third: Long,
    ): Unit {
        remote.receiveMultipleArguments(first, second, third)
    }
}
