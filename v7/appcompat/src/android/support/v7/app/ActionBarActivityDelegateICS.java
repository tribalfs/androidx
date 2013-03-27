/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.pm.ActivityInfo;
import android.os.Bundle;

class ActionBarActivityDelegateICS extends ActionBarActivityDelegateHC {

    ActionBarActivityDelegateICS(ActionBarActivity activity) {
        super(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Set framework uiOptions from the support metadata value
        if (UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW.equals(getUiOptionsFromMetadata())) {
            mActivity.getWindow().setUiOptions(ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW,
                    ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public ActionBar createSupportActionBar() {
        return new ActionBarImplICS(mActivity, mActivity);
    }

}
