/*
 * Copyright 2017 The Android Open Source Project
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

package android.support.wear.ambient;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.wear.widget.util.WakeLockRule;

import com.google.android.wearable.compat.WearableActivityController;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AmbientModeResumeTest {
    @Rule
    public final WakeLockRule mWakeLock = new WakeLockRule();

    @Rule
    public final ActivityTestRule<AmbientModeResumeTestActivity> mActivityRule =
            new ActivityTestRule<>(AmbientModeResumeTestActivity.class);

    @Test
    public void testActivityDefaults() throws Throwable {
        assertTrue(WearableActivityController.getLastInstance().isAutoResumeEnabled());
        assertFalse(WearableActivityController.getLastInstance().isAmbientEnabled());
    }
}
