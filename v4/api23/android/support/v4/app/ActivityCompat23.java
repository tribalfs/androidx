/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.support.v4.app;

import android.app.Activity;

class ActivityCompatApi23 {

    public static void requestPermissions(Activity activity, String[] permissions,
            int requestCode) {
        activity.requestPermissions(permissions, requestCode);
    }

    public static boolean shouldShowRequestPermissionRationale(Activity activity,
            String permission) {
        return activity.shouldShowRequestPermissionRationale(permission);
    }

    public interface OnRequestPermissionsResultCallback {
       public void onRequestPermissionsResult(int requestCode, String[] permissions,
                int[] grantResults);
    }
}
