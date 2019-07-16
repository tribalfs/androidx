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

package androidx.camera.testing;

import android.os.Build;

import org.junit.AssumptionViolatedException;

/** Utility functions of tests on CoreTestApp. */
public final class CoreAppTestUtil {

    /**
     * Check if this is compatible device for test.
     *
     * <p> Most devices should be compatible except devices with compatible issues.
     *
     */
    public static void assumeCompatibleDevice() {
        // TODO(b/134894604) This will be removed once the issue is fixed.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
                && Build.MODEL.contains("Nexus 5")) {
            throw new AssumptionViolatedException("Known issue, b/134894604.");
        }

    }


}
