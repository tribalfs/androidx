/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v13.app;

/**
 * Helper for accessing features in {@link android.app.Activity} in a backwards compatible fashion.
 *
 * @deprecated Use {@link android.support.v4.app.ActivityCompat
 * android.support.v4.app.ActivityCompat}.
 */
@Deprecated
public class ActivityCompat extends android.support.v4.app.ActivityCompat {
    /**
     * This class should not be instantiated, but the constructor must be
     * visible for the class to be extended.
     *
     * @deprecated Use {@link android.support.v4.app.ActivityCompat
     * android.support.v4.app.ActivityCompat}.
     */
    @Deprecated
    protected ActivityCompat() {
        // Not publicly instantiable, but may be extended.
    }
}
