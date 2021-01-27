/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.runtime.savedinstancestate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(AndroidJUnit4::class)
class RememberSavedInstanceStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val restorationTester = StateRestorationTester(rule)

    @Test
    fun simpleRestore() {
        var array: IntArray? = null
        restorationTester.setContent {
            array = rememberSavedInstanceState {
                intArrayOf(0)
            }
        }

        assertThat(array).isEqualTo(intArrayOf(0))

        rule.runOnUiThread {
            array!![0] = 1
            // we null it to ensure recomposition happened
            array = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(array).isEqualTo(intArrayOf(1))
    }

    @Test
    fun restoreWithSaver() {
        var holder: Holder? = null
        restorationTester.setContent {
            holder = rememberSavedInstanceState(saver = HolderSaver) {
                Holder(0)
            }
        }

        assertThat(holder).isEqualTo(Holder(0))

        rule.runOnUiThread {
            holder!!.value = 1
            // we null it to ensure recomposition happened
            holder = null
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(holder).isEqualTo(Holder(1))
    }

    @Test
    fun canBeSavedFromRegistryIsUsed() {
        var canBeSavedCalledWith: Any? = null

        restorationTester.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun canBeSaved(value: Any): Boolean {
                            canBeSavedCalledWith = value
                            return super.canBeSaved(value)
                        }
                    }
                }
            ) {
                val v = rememberSavedInstanceState { 1 }
                assertEquals(1, v)
            }
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(canBeSavedCalledWith).isEqualTo(1)
    }

    @Test
    fun providerProvidesCorrectlySavedValue() {
        var provider: () -> Any? = { error("will be overridden") }

        restorationTester.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun registerProvider(key: String, valueProvider: () -> Any?) {
                            provider = valueProvider
                            super.registerProvider(key, valueProvider)
                        }
                    }
                }
            ) {
                val v = rememberSavedInstanceState { 2 }
                assertEquals(2, v)
            }
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(provider.invoke()).isEqualTo(2)
    }

    @Test
    fun providerProvidesCorrectlySavedValueWithSaver() {
        var provider: () -> Any? = { error("will be overridden") }

        restorationTester.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun registerProvider(key: String, valueProvider: () -> Any?) {
                            provider = valueProvider
                            super.registerProvider(key, valueProvider)
                        }
                    }
                }
            ) {
                rememberSavedInstanceState(saver = HolderSaver) { Holder(4) }
            }
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(provider.invoke()).isEqualTo(4)
    }

    @Test
    fun unregistersFromPrevProviderAndRegistersToTheNewOne() {
        var unregisterCalledForKey: String? = null
        var registryFactory by mutableStateOf<(UiSavedStateRegistry) -> UiSavedStateRegistry>(
            value = {
                object : DelegateRegistry(it) {
                    override fun unregisterProvider(key: String, valueProvider: () -> Any?) {
                        unregisterCalledForKey = key
                        super.unregisterProvider(key, valueProvider)
                    }
                }
            }
        )

        rule.setContent {
            WrapRegistry(
                wrap = {
                    registryFactory(it)
                }
            ) {
                val v = rememberSavedInstanceState { 1 }
                assertEquals(1, v)
            }
        }

        var registerCalled = false

        rule.runOnUiThread {
            registryFactory = {
                object : DelegateRegistry(it) {
                    override fun registerProvider(key: String, valueProvider: () -> Any?) {
                        super.registerProvider(key, valueProvider)
                        // asserts that we unregistered from the previous registry and then
                        // registered with the same key
                        assertThat(key).isEqualTo(unregisterCalledForKey)
                        registerCalled = true
                    }
                }
            }
        }

        rule.mainClock.advanceTimeUntil { registerCalled }
    }

    @Test
    fun reregisterWhenTheKeyIsChanged() {
        var key by mutableStateOf("key1")
        val registeredKeys = mutableSetOf<String>()
        var registerCalled = 0

        rule.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun registerProvider(key: String, valueProvider: () -> Any?) {
                            super.registerProvider(key, valueProvider)
                            registeredKeys.add(key)
                            registerCalled++
                        }

                        override fun unregisterProvider(key: String, valueProvider: () -> Any?) {
                            super.unregisterProvider(key, valueProvider)
                            registeredKeys.remove(key)
                        }
                    }
                }
            ) {
                val v = rememberSavedInstanceState(key = key) { 1 }
                assertEquals(1, v)
            }
        }

        rule.mainClock.advanceTimeUntil { registerCalled == 1 }
        rule.runOnUiThread {
            key = "key2"
        }

        rule.mainClock.advanceTimeUntil { registerCalled == 2 }
        assertThat(registeredKeys).isEqualTo(mutableSetOf("key2"))
    }

    @Test
    fun theLatestPassedSaverIsUsed() {
        var saver by mutableStateOf(
            Saver<Int, Int>(
                save = { 1 },
                restore = { 1 }
            )
        )

        restorationTester.setContent {
            rememberSavedInstanceState(saver = saver) { 1 }
        }

        val latch = CountDownLatch(1)

        rule.runOnIdle {
            saver = Saver(
                save = {
                    latch.countDown()
                    1
                },
                restore = { 1 }
            )
        }

        restorationTester.emulateSavedInstanceStateRestore()
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun unregistersWhenDisposed() {
        var doEmit by mutableStateOf(true)
        var onUnregisterCalled = false

        rule.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun unregisterProvider(key: String, valueProvider: () -> Any?) {
                            onUnregisterCalled = true
                            super.unregisterProvider(key, valueProvider)
                        }
                    }
                }
            ) {
                if (doEmit) {
                    rememberSavedInstanceState { 1 }
                }
            }
        }

        rule.runOnUiThread {
            // assert that unregister is not yet called
            assertThat(onUnregisterCalled).isFalse()
            doEmit = false
        }

        rule.mainClock.advanceTimeUntil { onUnregisterCalled }
    }

    @Test
    fun customKey() {
        val passedKey = "test"
        var actualKey: String? = null
        rule.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun registerProvider(key: String, valueProvider: () -> Any?) {
                            actualKey = key
                            super.registerProvider(key, valueProvider)
                        }
                    }
                }
            ) {
                val v = rememberSavedInstanceState(key = passedKey) { 2 }
                assertEquals(2, v)
            }
        }

        assertThat(actualKey).isEqualTo(passedKey)
    }

    @Test
    fun emptyKeyIsNotUsed() {
        var actualKey: String? = null
        rule.setContent {
            WrapRegistry(
                wrap = {
                    object : DelegateRegistry(it) {
                        override fun registerProvider(key: String, valueProvider: () -> Any?) {
                            actualKey = key
                            super.registerProvider(key, valueProvider)
                        }
                    }
                }
            ) {
                val v = rememberSavedInstanceState(key = "") { 2 }
                assertEquals(2, v)
            }
        }

        assertThat(actualKey).isNotEmpty()
    }
}

@Composable
private fun WrapRegistry(
    wrap: @Composable (UiSavedStateRegistry) -> UiSavedStateRegistry,
    content: @Composable () -> Unit
) {
    Providers(
        AmbientUiSavedStateRegistry provides wrap(AmbientUiSavedStateRegistry.current!!),
        content = content
    )
}

private open class DelegateRegistry(original: UiSavedStateRegistry) :
    UiSavedStateRegistry by original