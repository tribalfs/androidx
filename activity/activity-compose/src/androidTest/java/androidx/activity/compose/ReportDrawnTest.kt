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
package androidx.activity.compose

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class ReportDrawnTest {
    @get:Rule
    val rule = createAndroidComposeRule<TestActivity>()

    @Test
    fun testReportFullyDrawnWhen() {
        var ready by mutableStateOf(false)
        var readyChecks = 0
        rule.setContent {
            ReportDrawnWhen {
                readyChecks++
                ready
            }
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        assertThat(readyChecks).isEqualTo(1)

        ready = true

        // Must recompose
        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
        assertThat(readyChecks).isEqualTo(2)

        // Shouldn't repeat the reportFullyDrawn()
        ready = false
        rule.waitForIdle()
        rule.activity.reportFullyDrawnCalled = false
        assertThat(readyChecks).isEqualTo(2)

        ready = true
        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        assertThat(readyChecks).isEqualTo(2)
    }

    @Test
    fun testReportFullyDrawnAfter() {
        val recomposeInt = mutableStateOf(0)
        val mutex = Mutex(locked = true)
        var lockChecks = 0
        rule.setContent {
            recomposeInt.value
            ReportDrawnAfter {
                lockChecks++
                mutex.withLock { }
            }
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        assertThat(lockChecks).isEqualTo(1)

        mutex.unlock()

        // Should complete as soon as the coroutine is scheduled, which is on the UI thread.
        // We just need to wait our turn for the UI thread:
        rule.runOnIdle {
            assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
            assertThat(lockChecks).isEqualTo(1)
        }

        // Shouldn't repeat the reportFullyDrawn()
        rule.activity.reportFullyDrawnCalled = false
        assertThat(mutex.tryLock()).isTrue()
        recomposeInt.value = 1
        rule.waitForIdle()

        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        assertThat(lockChecks).isEqualTo(1)
    }

    @Test
    fun waitUntilTwoAreDrawn() {
        val mutex = Mutex(locked = true)
        var conditionReady by mutableStateOf(false)
        rule.setContent {
            ReportDrawnWhen {
                conditionReady
            }
            ReportDrawnAfter {
                mutex.withLock { }
            }
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        conditionReady = true
        rule.waitForIdle()

        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        mutex.unlock()

        // Should complete as soon as the coroutine is scheduled, which is on the UI thread.
        // We just need to wait our turn for the UI thread:
        rule.runOnIdle {
            assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
        }
    }

    // same as above, but the order is swapped
    @Test
    fun waitUntilTwoAreDrawn2() {
        val mutex = Mutex(locked = true)
        var conditionReady by mutableStateOf(false)
        rule.setContent {
            ReportDrawnWhen {
                conditionReady
            }
            ReportDrawnAfter {
                mutex.withLock { }
            }
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        mutex.unlock()

        // Should complete as soon as the coroutine is scheduled, which is on the UI thread.
        // We just need to wait our turn for the UI thread:
        rule.runOnIdle {
            assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        }

        conditionReady = true
        rule.waitForIdle()

        assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
    }

    @Test
    fun waitForTwoDifferentComposeViews() {
        val mutex = Mutex(locked = true)
        var conditionReady by mutableStateOf(false)
        rule.setContent {
            AndroidView(factory = { context ->
                ComposeView(context).apply {
                    setContent {
                        ReportDrawnWhen {
                            conditionReady
                        }
                    }
                }
            })
            AndroidView(factory = { context ->
                ComposeView(context).apply {
                    setContent {
                        ReportDrawnAfter {
                            mutex.withLock { }
                        }
                    }
                }
            })
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        mutex.unlock()

        rule.runOnIdle {
            assertThat(rule.activity.reportFullyDrawnCalled).isFalse()
        }

        conditionReady = true
        rule.waitForIdle()

        assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
    }

    @Test
    fun removedCondition() {
        var condition1 by mutableStateOf(false)
        val condition2 by mutableStateOf(false)
        var useCondition2 by mutableStateOf(true)

        rule.setContent {
            ReportDrawnWhen {
                condition1
            }
            if (useCondition2) {
                ReportDrawnWhen {
                    condition2
                }
            }
        }

        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        condition1 = true
        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isFalse()

        useCondition2 = false
        rule.waitForIdle()
        assertThat(rule.activity.reportFullyDrawnCalled).isTrue()
    }

    @Test
    fun provideFullyDrawnReporter() {
        val fullyDrawnReporterOwner = object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter: FullyDrawnReporter
                get() = rule.activity.fullyDrawnReporter
        }
        lateinit var localValue: FullyDrawnReporterOwner
        rule.setContent {
            CompositionLocalProvider(
                LocalFullyDrawnReporterOwner provides fullyDrawnReporterOwner
            ) {
                localValue = LocalFullyDrawnReporterOwner.current!!
            }
        }
        assertThat(localValue).isSameInstanceAs(fullyDrawnReporterOwner)
    }
}
