package com.mysdk

public object PrivacySandboxThrowableParcelConverter {
    public fun toThrowableParcel(throwable: Throwable): PrivacySandboxThrowableParcel {
        val parcel = PrivacySandboxThrowableParcel()
        parcel.exceptionClass = throwable::class.qualifiedName
        parcel.errorMessage = throwable.message
        parcel.stackTrace = throwable.stackTrace.map {
            val stackFrame = ParcelableStackFrame()
            stackFrame.declaringClass = it.className
            stackFrame.methodName = it.methodName
            stackFrame.fileName = it.fileName
            stackFrame.lineNumber = it.lineNumber
            stackFrame
        }.toTypedArray()
        throwable.cause?.let {
            parcel.cause = toThrowableParcel(it)
        }
        parcel.suppressedExceptions =
            throwable.suppressedExceptions.map {
                toThrowableParcel(it)
            }.toTypedArray()
        return parcel
    }
}
