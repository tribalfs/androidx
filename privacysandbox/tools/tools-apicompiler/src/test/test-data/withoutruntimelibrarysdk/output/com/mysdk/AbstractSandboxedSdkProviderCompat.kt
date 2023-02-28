package com.mysdk

import android.app.sdksandbox.SandboxedSdk
import android.app.sdksandbox.SandboxedSdkProvider
import android.content.Context
import android.os.Bundle
import android.view.View
import kotlin.Int

public abstract class AbstractSandboxedSdkProviderCompat : SandboxedSdkProvider() {
  public override fun onLoadSdk(params: Bundle): SandboxedSdk {
    val sdk = createWithoutRuntimeLibrarySdk(context!!)
    return SandboxedSdk(WithoutRuntimeLibrarySdkStubDelegate(sdk))
  }

  public override fun getView(
    windowContext: Context,
    params: Bundle,
    width: Int,
    height: Int,
  ): View = throw
      UnsupportedOperationException("This SDK doesn't support explicit SurfaceView requests.")

  protected abstract fun createWithoutRuntimeLibrarySdk(context: Context): WithoutRuntimeLibrarySdk
}
