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

package androidx.ui

import android.app.Activity
import android.util.TypedValue
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.testutils.PollingCheck
import androidx.ui.core.dp
import androidx.ui.core.toPx
import androidx.ui.port.integration.ImageDrawTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class DimensionDeviceTest {
    @get:Rule
    val activityTestRule = ActivityTestRule<ImageDrawTest.Companion.TestActivity>(
        ImageDrawTest.Companion.TestActivity::class.java
    )
    private lateinit var activity: Activity

    @Before
    fun setup() {
        activity = activityTestRule.activity
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @Test
    fun dimensionCalculation() {
        val dm = activity.resources.displayMetrics
        val dp10InPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dm)
        assertEquals(dp10InPx, 10.dp.toPx(activity), 0.01f)
    }
}