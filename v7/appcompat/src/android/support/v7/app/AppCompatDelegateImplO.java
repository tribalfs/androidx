/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.v7.app;

import android.content.Context;
import android.support.annotation.RequiresApi;
import android.view.KeyEvent;
import android.view.Window;

@RequiresApi(26)
class AppCompatDelegateImplO extends AppCompatDelegateImplN {

    AppCompatDelegateImplO(Context context, Window window, AppCompatCallback callback) {
        super(context, window, callback);
    }

    @Override
    public boolean checkActionBarFocusKey(KeyEvent event) {
        // In O+, ActionBar's make use of cluster navigation instead of a specific hotkey
        return false;
    }
}
