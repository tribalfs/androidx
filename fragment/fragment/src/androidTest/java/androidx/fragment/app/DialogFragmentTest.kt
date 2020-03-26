/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.fragment.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.test.EmptyFragmentTestActivity
import androidx.fragment.test.R
import androidx.lifecycle.ViewModelStore
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class DialogFragmentTest {
    @get:Rule
    val activityTestRule = ActivityTestRule(EmptyFragmentTestActivity::class.java)

    @Test
    fun testDialogFragmentShows() {
        val fragment = TestDialogFragment()
        fragment.show(activityTestRule.activity.supportFragmentManager, null)
        activityTestRule.runOnUiThread {
            activityTestRule.activity.supportFragmentManager.executePendingTransactions()
        }

        assertWithMessage("Dialog was not being shown")
            .that(fragment.dialog?.isShowing)
            .isTrue()
    }

    @UiThreadTest
    @Test
    fun testDialogFragmentShowsNow() {
        val fragment = TestDialogFragment()
        fragment.showNow(activityTestRule.activity.supportFragmentManager, null)

        assertWithMessage("Dialog was not being shown")
            .that(fragment.dialog?.isShowing)
            .isTrue()
    }

    @UiThreadTest
    @Test
    fun testDialogFragmentInLayout() {
        val fragment = TestLayoutDialogFragment()
        activityTestRule.activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()

        assertWithMessage("Dialog should be added to the layout")
            .that(activityTestRule.activity.findViewById<View>(R.id.textA))
            .isNotNull()
    }

    @UiThreadTest
    @Test
    fun testDialogFragmentSetShowDialog() {
        val fragment = TestDialogFragment(true)
        fragment.showNow(activityTestRule.activity.supportFragmentManager, null)

        assertWithMessage("Dialog was not null")
            .that(fragment.dialog)
            .isNull()
    }

    @UiThreadTest
    @Test
    fun testDialogFragmentSetContentViewCalledBeforeStart() {
        val fragment = TestLayoutDialogFragment()

        activityTestRule.runOnUiThread {
            fragment.showNow(activityTestRule.activity.supportFragmentManager, null)
        }

        assertWithMessage("Dialog should have called setContentView by onStart")
            .that(fragment.parentSetInStart)
            .isTrue()
    }

    @Test
    fun testCancelDialog() {
        val dialogFragment = TestDialogFragment()
        val fm = activityTestRule.activity.supportFragmentManager

        activityTestRule.runOnUiThread {
            fm.beginTransaction()
                .add(dialogFragment, null)
                .commitNow()
        }

        val dialog = dialogFragment.requireDialog()
        activityTestRule.runOnUiThread {
            dialog.cancel()
        }

        activityTestRule.runOnUiThread {
            assertWithMessage("OnCancel should have been called")
                .that(dialogFragment.onCancelCalled)
                .isTrue()
        }
    }

    @Test
    fun testCancelDestroyedDialog() {
        val dialogFragment = TestDialogFragment()
        val fm = activityTestRule.activity.supportFragmentManager

        activityTestRule.runOnUiThread {
            fm.beginTransaction()
                .add(dialogFragment, null)
                .commitNow()
        }

        val dialog = dialogFragment.requireDialog()

        activityTestRule.runOnUiThread {
            dialog.cancel()
            fm.beginTransaction()
                .remove(dialogFragment)
                .commitNow()
        }

        activityTestRule.runOnUiThread {
            assertWithMessage("OnCancel should not have been called")
                .that(dialogFragment.onCancelCalled)
                .isFalse()
        }
    }

    @Test
    @UiThreadTest
    fun testSavedInstanceStateAlertDialog() {
        val viewModelStore = ViewModelStore()
        val fc1 = activityTestRule.startupFragmentController(viewModelStore)
        val fm1 = fc1.supportFragmentManager

        val dialogFragment = TestDialogFragment()

        fm1.beginTransaction()
            .add(dialogFragment, "dialog")
            .commitNow()

        dialogFragment.requireDialog().findViewById<EditText>(R.id.editText).apply {
            setText("saved", TextView.BufferType.EDITABLE)
        }

        fc1.dispatchPause()
        val savedState = fc1.saveAllState()
        fc1.dispatchStop()
        fc1.dispatchDestroy()

        val fc2 = activityTestRule.startupFragmentController(viewModelStore, savedState)
        val fm2 = fc2.supportFragmentManager

        val restoredDialogFragment = fm2.findFragmentByTag("dialog") as TestDialogFragment
        assertWithMessage("Dialog fragment was not restored")
            .that(restoredDialogFragment).isNotNull()

        val restoredDialog = restoredDialogFragment.requireDialog()

        val restoredText = restoredDialog.findViewById<EditText>(R.id.editText).text.toString()

        assertWithMessage("onRestoreInstanceState called before setContent")
            .that(restoredText)
            .isEqualTo("saved")

        // Bring the state back down to destroyed before we finish the test
        fc2.shutdown(viewModelStore)
    }

    @Test
    @UiThreadTest
    fun testSavedViewInstanceState() {
        val viewModelStore = ViewModelStore()
        val fc1 = activityTestRule.startupFragmentController(viewModelStore)
        val fm1 = fc1.supportFragmentManager

        val dialogFragment = RestoreViewDialogFragment()
        val expectedText = "saved"

        fm1.beginTransaction()
            .add(dialogFragment, "dialog")
            .commitNow()

        dialogFragment.requireView().findViewById<EditText>(R.id.editText).apply {
            setText(expectedText, TextView.BufferType.EDITABLE)
        }

        fc1.dispatchPause()
        val savedState = fc1.saveAllState()
        fc1.dispatchStop()
        fc1.dispatchDestroy()

        val fc2 = activityTestRule.startupFragmentController(viewModelStore, savedState)
        val fm2 = fc2.supportFragmentManager

        val restoredDialogFragment = fm2.findFragmentByTag("dialog") as RestoreViewDialogFragment
        assertWithMessage("Dialog fragment was not restored")
            .that(restoredDialogFragment).isNotNull()

        val restoredText = restoredDialogFragment.requireView()
            .findViewById<EditText>(R.id.editText).text.toString()

        assertWithMessage("State of EditText was not restored")
            .that(restoredText)
            .isEqualTo(expectedText)

        // Bring the state back down to destroyed before we finish the test
        fc2.shutdown(viewModelStore)
    }

    @Test
    @UiThreadTest
    fun testSavedInstanceState() {
        val viewModelStore = ViewModelStore()
        val fc1 = activityTestRule.startupFragmentController(viewModelStore)
        val fm1 = fc1.supportFragmentManager

        val dialogFragment = TestLayoutDialogFragment()

        fm1.beginTransaction()
            .add(dialogFragment, "dialog")
            .commitNow()

        fc1.dispatchPause()
        val savedState = fc1.saveAllState()
        fc1.dispatchStop()
        fc1.dispatchDestroy()

        val fc2 = activityTestRule.startupFragmentController(viewModelStore, savedState)
        val fm2 = fc2.supportFragmentManager

        val restoredDialogFragment = fm2.findFragmentByTag("dialog") as TestLayoutDialogFragment
        assertWithMessage("Dialog fragment was not restored")
            .that(restoredDialogFragment).isNotNull()

        val restoredDialog = restoredDialogFragment.dialog as RestoreDialog

        assertWithMessage("onRestoreInstanceState called before setContent")
            .that(restoredDialog.restoreAfterSetContent)
            .isTrue()

        // Bring the state back down to destroyed before we finish the test
        fc2.shutdown(viewModelStore)
    }

    @Suppress("DEPRECATION")
    @Test
    @UiThreadTest
    fun testRetainedSavedInstanceState() {
        val viewModelStore = ViewModelStore()
        val fc1 = activityTestRule.startupFragmentController(viewModelStore)
        val fm1 = fc1.supportFragmentManager

        val dialogFragment = TestLayoutDialogFragment()
        dialogFragment.retainInstance = true

        fm1.beginTransaction()
            .add(dialogFragment, "dialog")
            .commitNow()

        fc1.dispatchPause()
        val savedState = fc1.saveAllState()
        fc1.dispatchStop()
        fc1.dispatchDestroy()

        val fc2 = activityTestRule.startupFragmentController(viewModelStore, savedState)
        val fm2 = fc2.supportFragmentManager

        val restoredDialogFragment = fm2.findFragmentByTag("dialog") as TestLayoutDialogFragment
        assertWithMessage("Dialog fragment was not restored")
            .that(restoredDialogFragment).isNotNull()

        val restoredDialog = restoredDialogFragment.dialog as RestoreDialog

        assertWithMessage("onRestoreInstanceState called before setContent")
            .that(restoredDialog.restoreAfterSetContent)
            .isTrue()

        // Bring the state back down to destroyed before we finish the test
        fc2.shutdown(viewModelStore)
    }

    class TestDialogFragment(val setShowsDialog: Boolean = false) : DialogFragment() {
        var onCancelCalled = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            if (setShowsDialog) {
                showsDialog = false
            }
            val view = layoutInflater.inflate(R.layout.with_edit_text, null, false)
            return AlertDialog.Builder(context)
                .setTitle("Test")
                .setMessage("Message")
                .setView(view)
                .setPositiveButton("Button", null)
                .create()
        }

        override fun onCancel(dialog: DialogInterface) {
            super.onCancel(dialog)
            onCancelCalled = true
        }
    }

    class TestLayoutDialogFragment : DialogFragment(R.layout.fragment_a) {
        var parentSetInStart = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return RestoreDialog(requireContext())
        }

        override fun onStart() {
            parentSetInStart = requireView().parent != null
            super.onStart()
        }
    }

    class RestoreViewDialogFragment : DialogFragment(R.layout.with_edit_text)

    class RestoreDialog(context: Context) : Dialog(context) {
        var setContentCalled = false
        var restoreAfterSetContent = false

        override fun setContentView(view: View) {
            super.setContentView(view)
            setContentCalled = true
            restoreAfterSetContent = false
        }

        override fun onRestoreInstanceState(savedInstanceState: Bundle) {
            super.onRestoreInstanceState(savedInstanceState)
            if (setContentCalled) {
                restoreAfterSetContent = true
                setContentCalled = false
            }
        }
    }
}
