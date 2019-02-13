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
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.test.EmptyFragmentTestActivity
import androidx.lifecycle.GenericLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    @Test
    fun testDialogFragmentDismissOnFinish() {
        val fragment = TestDialogFragment()
        activityTestRule.runOnUiThread {
            fragment.showNow(activityTestRule.activity.supportFragmentManager, null)
        }

        assertWithMessage("Dialog was not being shown")
            .that(fragment.dialog?.isShowing)
            .isTrue()

        var dialogIsNonNull = false
        var isShowing = false
        var onDismissCalledCount = 0
        val countDownLatch = CountDownLatch(3)
        activityTestRule.runOnUiThread {
            fragment.lifecycle.addObserver(GenericLifecycleObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val dialog = fragment.dialog
                    dialogIsNonNull = dialog != null
                    isShowing = dialog != null && dialog.isShowing
                    countDownLatch.countDown()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    onDismissCalledCount = fragment.onDismissCalledCount
                    countDownLatch.countDown()
                }
            })
        }
        var dismissOnMainThread = false
        var dismissCalled = false
        fragment.dismissCallback = {
            dismissCalled = true
            dismissOnMainThread = Looper.myLooper() == Looper.getMainLooper()
            countDownLatch.countDown()
        }

        activityTestRule.finishActivity()

        countDownLatch.await(1, TimeUnit.SECONDS)

        assertWithMessage("Dialog should be dismissed")
            .that(dismissCalled)
            .isTrue()
        assertWithMessage("Dismiss should always be called on the main thread")
            .that(dismissOnMainThread)
            .isTrue()
        assertWithMessage("onDismiss() should be called before onDestroy()")
            .that(onDismissCalledCount)
            .isEqualTo(1)
        assertWithMessage("Dialog should not be null in onStop()")
            .that(dialogIsNonNull)
            .isTrue()
        assertWithMessage("Dialog should still be showing in onStop() during the normal lifecycle")
            .that(isShowing)
            .isTrue()
    }

    @Test
    fun testDialogFragmentDismiss() {
        val fragment = TestDialogFragment()
        activityTestRule.runOnUiThread {
            fragment.showNow(activityTestRule.activity.supportFragmentManager, null)
        }

        assertWithMessage("Dialog was not being shown")
            .that(fragment.dialog?.isShowing)
            .isTrue()

        var dialogIsNonNull = false
        var isShowing = false
        var onDismissCalledCount = 0
        val countDownLatch = CountDownLatch(3)
        activityTestRule.runOnUiThread {
            fragment.lifecycle.addObserver(GenericLifecycleObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    val dialog = fragment.dialog
                    dialogIsNonNull = dialog != null
                    isShowing = dialog != null && dialog.isShowing
                    countDownLatch.countDown()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    onDismissCalledCount = fragment.onDismissCalledCount
                    countDownLatch.countDown()
                }
            })
        }
        var dismissOnMainThread = false
        var dismissCalled = false
        fragment.dismissCallback = {
            dismissCalled = true
            dismissOnMainThread = Looper.myLooper() == Looper.getMainLooper()
            countDownLatch.countDown()
        }

        fragment.dismiss()

        countDownLatch.await(1, TimeUnit.SECONDS)

        assertWithMessage("Dialog should be dismissed")
            .that(dismissCalled)
            .isTrue()
        assertWithMessage("Dismiss should always be called on the main thread")
            .that(dismissOnMainThread)
            .isTrue()
        assertWithMessage("onDismiss() should be called before onDestroy()")
            .that(onDismissCalledCount)
            .isEqualTo(1)
        assertWithMessage("Dialog should not be null in onStop()")
            .that(dialogIsNonNull)
            .isTrue()
        assertWithMessage("Dialog should not be showing in onStop() when manually dismissed")
            .that(isShowing)
            .isFalse()

        assertWithMessage("Dialog should be null after dismiss()")
            .that(fragment.dialog)
            .isNull()
    }

    @Test
    fun testDialogFragmentDismissBeforeOnDestroy() {
        val fragment = TestDialogFragment()
        activityTestRule.runOnUiThread {
            fragment.showNow(activityTestRule.activity.supportFragmentManager, null)
        }

        var onDismissCalledCount = 0
        val countDownLatch = CountDownLatch(1)
        activityTestRule.runOnUiThread {
            fragment.lifecycle.addObserver(GenericLifecycleObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    onDismissCalledCount = fragment.onDismissCalledCount
                    countDownLatch.countDown()
                }
            })
            // Now dismiss the Fragment
            fragment.dismiss()
        }

        countDownLatch.await(1, TimeUnit.SECONDS)

        assertWithMessage("onDismiss() should be called only once before onDestroy()")
            .that(onDismissCalledCount)
            .isEqualTo(1)
    }

    class TestDialogFragment : DialogFragment() {

        var onDismissCalledCount = 0
        var dismissCallback: () -> Unit = {}

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(context)
                .setTitle("Test")
                .setMessage("Message")
                .setPositiveButton("Button", null)
                .create()
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            onDismissCalledCount++
            dismissCallback.invoke()
        }
    }
}
