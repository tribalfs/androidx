/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.privacysandbox.ui.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.SurfaceControlViewHost
import android.view.SurfaceView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.core.IRemoteSessionClient
import androidx.privacysandbox.ui.core.IRemoteSessionController
import androidx.privacysandbox.ui.core.ISandboxedUiAdapter
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Provides an adapter created from a supplied Bundle which acts as a proxy between the host app and
 * the Binder provided by the provider of content.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object SandboxedUiAdapterFactory {

    private const val TAG = "PrivacySandboxUiLib"
    // Bundle key is a binary compatibility requirement
    private const val UI_ADAPTER_BINDER = "uiAdapterBinder"
    private const val TEST_ONLY_USE_REMOTE_ADAPTER = "testOnlyUseRemoteAdapter"

    /**
     * @throws IllegalArgumentException if {@code coreLibInfo} does not contain a Binder with the
     * key UI_ADAPTER_BINDER
     */
    fun createFromCoreLibInfo(coreLibInfo: Bundle): SandboxedUiAdapter {
        val uiAdapterBinder = requireNotNull(coreLibInfo.getBinder(UI_ADAPTER_BINDER)) {
            "Invalid bundle, missing $UI_ADAPTER_BINDER."
        }
        val adapterInterface = ISandboxedUiAdapter.Stub.asInterface(
            uiAdapterBinder
        )

        val forceUseRemoteAdapter = coreLibInfo.getBoolean(TEST_ONLY_USE_REMOTE_ADAPTER)
        val isLocalBinder = uiAdapterBinder.queryLocalInterface(
                ISandboxedUiAdapter.DESCRIPTOR) != null
        val useLocalAdapter = !forceUseRemoteAdapter && isLocalBinder
        Log.d(TAG, "useLocalAdapter=$useLocalAdapter")

        return if (useLocalAdapter) {
            LocalAdapter(adapterInterface)
        } else {
            RemoteAdapter(adapterInterface)
        }
    }

    /**
     * [LocalAdapter] fetches UI from a provider living on same process as the client but on a
     * different class loader.
     */
    private class LocalAdapter(private val adapterInterface: ISandboxedUiAdapter) :
        SandboxedUiAdapter {
        private val uiProviderBinder = adapterInterface.asBinder()

        @SuppressLint("BanUncheckedReflection") // using reflection on library classes
        override fun openSession(
            context: Context,
            windowInputToken: IBinder,
            initialWidth: Int,
            initialHeight: Int,
            isZOrderOnTop: Boolean,
            clientExecutor: Executor,
            client: SandboxedUiAdapter.SessionClient
        ) {
            try {
                // openSession call needs to be forwarded to uiProvider object instantiated
                // on a different classloader.
                val uiProviderClassLoader = uiProviderBinder.javaClass.classLoader
                val classOnUiProviderClassLoader = Class.forName(uiProviderBinder::class.java.name,
                        /*initialize=*/false, uiProviderBinder.javaClass.classLoader)
                val openSessionMethod = classOnUiProviderClassLoader.methods.first {
                        method -> method.name.equals("openSession")
                }

                // We can't pass the client object as-is since it's been created on a different
                // classloader.
                val targetSessionClientClass = Class.forName(
                    SandboxedUiAdapter.SessionClient::class.java.name,
                    /*initialize=*/ false,
                    uiProviderClassLoader
                )
                val sessionClientProxy = Proxy.newProxyInstance(
                    uiProviderClassLoader,
                    arrayOf(targetSessionClientClass),
                    SessionClientProxyHandler(client)
                )

                openSessionMethod.invoke(uiProviderBinder, context, windowInputToken, initialWidth,
                        initialHeight, isZOrderOnTop, clientExecutor, sessionClientProxy)
            } catch (exception: Throwable) {
                client.onSessionError(exception)
            }
        }

        private inner class SessionClientProxyHandler(
            private val origClient: SandboxedUiAdapter.SessionClient,
        ) : InvocationHandler {

            @SuppressLint("BanUncheckedReflection") // using reflection on library classes
            override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
                return when (method.name) {
                    "onSessionOpened" -> {
                        args!! // This method will always have an argument, so safe to !!

                        // We have to forward the call to original client, but it won't
                        // recognize Session class on targetClassLoader. We need another proxy.
                        val origSessionClass = Class.forName(
                            SandboxedUiAdapter.Session::class.java.name,
                            /*initialize=*/ false,
                            origClient.javaClass.classLoader
                        )
                        val sessionProxy = Proxy.newProxyInstance(
                            origClient.javaClass.classLoader,
                            arrayOf(origSessionClass),
                            SessionProxyHandler(args[0])
                        )

                        val methodOrig = origClient.javaClass.getMethod("onSessionOpened",
                        SandboxedUiAdapter.Session::class.java)
                        methodOrig.invoke(origClient, sessionProxy)
                    }
                    "onSessionError" -> {
                        args!! // This method will always have an argument, so safe to !!

                        val throwable = args[0] as Throwable
                        val methodOrig = origClient.javaClass.getMethod("onSessionError",
                        Throwable::class.java)
                        methodOrig.invoke(origClient, throwable)
                    }
                    "onResizeRequested" -> {
                        args!! // This method will always have an argument, so safe to !!

                        val methodOrig = origClient.javaClass.getMethod("onResizeRequested",
                                Int::class.java, Int::class.java)
                        methodOrig.invoke(origClient, args[0], args[1])
                    }
                    "toString" -> {
                        origClient.javaClass.getMethod("toString").invoke(origClient)
                    }
                    else -> {
                        // TODO(b/282918647): Implement other methods required
                        throw UnsupportedOperationException(
                            "Unexpected method call object:$proxy, method: $method, args: $args"
                        )
                    }
                }
            }
        }

        /**
         * Create [SandboxedUiAdapter.Session] on [targetClassLoader] that proxies to [origClient]
         */
        private inner class SessionProxyHandler(
                private val origClient: Any,
            ) : InvocationHandler {

            @SuppressLint("BanUncheckedReflection") // using reflection on library classes
            override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
                return when (method.name) {
                    "close" -> {
                        origClient.javaClass.getMethod("close").invoke(origClient)
                    }
                    "getView" -> {
                        origClient.javaClass.getMethod("getView").invoke(origClient)
                    }
                    "notifyResized" -> {
                        args!! // This method will always have an argument, so safe to !!

                        val methodOrig = origClient.javaClass.getMethod("notifyResized",
                                Int::class.java, Int::class.java)
                        methodOrig.invoke(origClient, args[0], args[1])
                    }
                    "notifyZOrderChanged" -> {
                        args!! // This method will always have an argument, so safe to !!

                        val methodOrig = origClient.javaClass.getMethod("notifyZOrderChanged",
                                Boolean::class.java)
                        methodOrig.invoke(origClient, args[0])
                    }
                    "notifyConfigurationChanged" -> {
                        args!! // This method will always have an argument, so safe to !!

                        val methodOrig = origClient.javaClass.getMethod(
                            "notifyConfigurationChanged", Configuration::class.java)
                        methodOrig.invoke(origClient, args[0])
                    }
                    "toString" -> {
                        origClient.javaClass.getMethod("toString").invoke(origClient)
                    }
                    else -> {
                        // TODO(b/282918647): Implement other methods required
                        throw UnsupportedOperationException(
                            "Unexpected method call object:$proxy, method: $method, args: $args"
                        )
                    }
                }
            }
        }
    }

    /**
     * [RemoteAdapter] fetches content from a provider living on a different process.
     */
    private class RemoteAdapter(private val adapterInterface: ISandboxedUiAdapter) :
        SandboxedUiAdapter {

        override fun openSession(
            context: Context,
            windowInputToken: IBinder,
            initialWidth: Int,
            initialHeight: Int,
            isZOrderOnTop: Boolean,
            clientExecutor: Executor,
            client: SandboxedUiAdapter.SessionClient
        ) {
            val mDisplayManager =
                context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displayId = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).displayId

            adapterInterface.openRemoteSession(
                windowInputToken,
                displayId,
                initialWidth,
                initialHeight,
                isZOrderOnTop,
                RemoteSessionClient(context, client, clientExecutor)
            )
        }

        class RemoteSessionClient(
            val context: Context,
            val client: SandboxedUiAdapter.SessionClient,
            val clientExecutor: Executor
        ) : IRemoteSessionClient.Stub() {

            override fun onRemoteSessionOpened(
                surfacePackage: SurfaceControlViewHost.SurfacePackage,
                remoteSessionController: IRemoteSessionController,
                isZOrderOnTop: Boolean
            ) {
                val surfaceView = SurfaceView(context)
                surfaceView.setChildSurfacePackage(surfacePackage)
                surfaceView.setZOrderOnTop(isZOrderOnTop)

                clientExecutor.execute {
                    client.onSessionOpened(SessionImpl(surfaceView, remoteSessionController))
                }
            }

            override fun onRemoteSessionError(errorString: String) {
                clientExecutor.execute {
                    client.onSessionError(Throwable(errorString))
                }
            }

            override fun onResizeRequested(width: Int, height: Int) {
                clientExecutor.execute {
                    client.onResizeRequested(width, height)
                }
            }
        }

        private class SessionImpl(
            val surfaceView: SurfaceView,
            val remoteSessionController: IRemoteSessionController
        ) : SandboxedUiAdapter.Session {

            override val view: View = surfaceView

            override fun notifyConfigurationChanged(configuration: Configuration) {
                remoteSessionController.notifyConfigurationChanged(configuration)
            }

            override fun notifyResized(width: Int, height: Int) {
                remoteSessionController.notifyResized(width, height)
            }

            override fun notifyZOrderChanged(isZOrderOnTop: Boolean) {
                surfaceView.setZOrderOnTop(isZOrderOnTop)
                remoteSessionController.notifyZOrderChanged(isZOrderOnTop)
            }

            override fun close() {
                remoteSessionController.close()
            }
        }
    }
}
