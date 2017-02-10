/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.support.lifecycle;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

/**
 * Activity that implements {@link LifecycleProvider}.
 * <p>
 * This class is a temporary implementation detail until Lifecycles are integrated with support
 * library.
 */
public class LifecycleActivity extends FragmentActivity implements LifecycleProvider {

    private final ActivityLifecycleDispatcher mDispatcher = new ActivityLifecycleDispatcher(this,
            this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDispatcher.onActivityPostSuperOnCreate();
    }

    @Override
    protected void onResume() {
        mDispatcher.onActivityPreSuperOnResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mDispatcher.onActivityPreSuperOnPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        mDispatcher.onActivityPreSuperOnStop();
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        mDispatcher.onActivityPreSuperOnDestroy();
        super.onDestroy();
    }

    @Override
    public Lifecycle getLifecycle() {
        return mDispatcher.getLifecycle();
    }
}
