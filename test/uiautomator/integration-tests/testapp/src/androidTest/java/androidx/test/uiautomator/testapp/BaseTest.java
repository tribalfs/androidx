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

package androidx.test.uiautomator.testapp;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class BaseTest {

    private static final long TIMEOUT_MS = 10_000;
    protected static final String TEST_APP = "androidx.test.uiautomator.testapp";
    protected static final int DEFAULT_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;

    protected UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.wakeUp();
        mDevice.pressHome();
    }

    protected void launchTestActivity(@NonNull Class<? extends Activity> activity) {
        launchTestActivity(activity, new Intent().setFlags(DEFAULT_FLAGS));
    }

    protected void launchTestActivity(@NonNull Class<? extends Activity> activity,
            @NonNull Intent intent) {
        Context context = ApplicationProvider.getApplicationContext();
        context.startActivity(new Intent(intent).setClass(context, activity));
        assertTrue("Test app not visible after launching activity",
                mDevice.wait(Until.hasObject(By.pkg(TEST_APP)), TIMEOUT_MS));
    }
}
