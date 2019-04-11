/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.material

import androidx.test.filters.MediumTest
import androidx.ui.core.CraneWrapper
import androidx.ui.core.TestTag
import androidx.ui.layout.Column
import androidx.ui.test.DisableTransitions
import androidx.ui.test.android.AndroidUiTestRunner
import androidx.ui.test.assertIsChecked
import androidx.ui.test.assertIsNotChecked
import androidx.ui.test.assertSemanticsIsEqualTo
import androidx.ui.test.createFullSemantics
import androidx.ui.test.doClick
import androidx.ui.test.findByTag
import com.google.r4a.composer
import com.google.r4a.state
import com.google.r4a.unaryPlus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class SwitchUiTest : AndroidUiTestRunner() {

    @get:Rule
    val disableTransitions = DisableTransitions()

    private val defaultUncheckedSwitchSemantics = createFullSemantics(
        enabled = false,
        checked = false
    )
    private val defaultCheckedSwitchSemantics = createFullSemantics(
        enabled = false,
        checked = true
    )
    private val defaultSwitchTag = "switch"

    @Test
    fun SwitchTest_defaultSemantics() {
        setContent {
            <CraneWrapper>
                <MaterialTheme>
                    <Column>
                        <TestTag tag="checked">
                            <Switch checked=true />
                        </TestTag>
                        <TestTag tag="unchecked">
                            <Switch checked=false />
                        </TestTag>
                    </Column>
                </MaterialTheme>
            </CraneWrapper>
        }

        findByTag("checked").assertSemanticsIsEqualTo(defaultCheckedSwitchSemantics)
        findByTag("unchecked").assertSemanticsIsEqualTo(defaultUncheckedSwitchSemantics)
    }

    @Test
    fun SwitchTest_toggle() {
        setContent {
            <CraneWrapper>
                <MaterialTheme>
                    val (checked, onChecked) = +state { false }
                    <TestTag tag=defaultSwitchTag>
                        <Switch checked onChecked />
                    </TestTag>
                </MaterialTheme>
            </CraneWrapper>
        }
        findByTag(defaultSwitchTag)
            .assertIsNotChecked()
            .doClick()
            .assertIsChecked()
    }

    @Test
    fun SwitchTest_toggleTwice() {
        setContent {
            <CraneWrapper>
                <MaterialTheme>
                    val (checked, onChecked) = +state { false }
                    <TestTag tag=defaultSwitchTag>
                        <Switch checked onChecked />
                    </TestTag>
                </MaterialTheme>
            </CraneWrapper>
        }
        findByTag(defaultSwitchTag)
            .assertIsNotChecked()
            .doClick()
            .assertIsChecked()
            .doClick()
            .assertIsNotChecked()
    }

    @Test
    fun SwitchTest_uncheckableWithNoLambda() {
        setContent {
            <CraneWrapper>
                <MaterialTheme>
                    val (checked, _) = +state { false }
                    <TestTag tag=defaultSwitchTag>
                        <Switch checked />
                    </TestTag>
                </MaterialTheme>
            </CraneWrapper>
        }
        findByTag(defaultSwitchTag)
            .assertIsNotChecked()
            .doClick()
            .assertIsNotChecked()
    }
}