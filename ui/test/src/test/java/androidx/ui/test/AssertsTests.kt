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

package androidx.ui.test

import androidx.ui.core.semantics.SemanticsConfiguration
import androidx.ui.test.helpers.FakeUiTestRunner
import org.junit.Test

class AssertsTests {
    @Test
    fun assertIsVisible_forVisibleElement_isOk() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isHidden = false
            })
            .findByTag("test")
            .assertIsVisible()
    }

    @Test(expected = AssertionError::class)
    fun assertIsVisible_forNotVisibleElement_throwsError() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isHidden = true
            })
            .findByTag("test")
            .assertIsVisible()
    }

    @Test
    fun assertIsHidden_forHiddenElement_isOk() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isHidden = true
            })
            .findByTag("test")
            .assertIsHidden()
    }

    @Test(expected = AssertionError::class)
    fun assertIsHidden_forNotHiddenElement_throwsError() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isHidden = false
            })
            .findByTag("test")
            .assertIsHidden()
    }

    @Test
    fun assertIsChecked_forCheckedElement_isOk() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isChecked = true
            })
            .findByTag("test")
            .assertIsChecked()
    }

    @Test(expected = AssertionError::class)
    fun assertIsChecked_forNotCheckedElement_throwsError() {
        FakeUiTestRunner()
            .withProperties(SemanticsConfiguration().also {
                it.testTag = "test"
                it.isChecked = false
            })
            .findByTag("test")
            .assertIsHidden()
    }

    @Test(expected = AssertionError::class)
    fun assertIsSelected_forNotSelectedElement_throwsError() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isSelected = false
                }
            )
            .findByTag("test")
            .assertIsSelected(true)
    }

    @Test
    fun assertIsSelected_forSelectedElement_isOk() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isSelected = true
                }
            )
            .findByTag("test")
            .assertIsSelected(true)
    }

    @Test(expected = AssertionError::class)
    fun assertIsNotSelected_forSelectedElement_throwsError() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isSelected = true
                }
            )
            .findByTag("test")
            .assertIsSelected(false)
    }

    @Test
    fun assertIsNotSelected_forNotSelectedElement_isOk() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isSelected = false
                }
            )
            .findByTag("test")
            .assertIsSelected(false)
    }

    @Test(expected = AssertionError::class)
    fun assertItemInExclusiveGroup_forItemNotInGroup_throwsError() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isInMutuallyExclusiveGroup = false
                }
            )
            .findByTag("test")
            .assertIsInMutuallyExclusiveGroup()
    }

    @Test
    fun assertItemInExclusiveGroup_forItemInGroup_isOk() {
        FakeUiTestRunner()
            .withProperties(
                SemanticsConfiguration().also {
                    it.testTag = "test"
                    it.isInMutuallyExclusiveGroup = true
                }
            )
            .findByTag("test")
            .assertIsInMutuallyExclusiveGroup()
    }
}