package com.sdkwithcallbacks

import androidx.privacysandbox.ui.core.SandboxedUiAdapter

public interface MyUiInterface : SandboxedUiAdapter {
    public fun doUiStuff(): Unit
}
