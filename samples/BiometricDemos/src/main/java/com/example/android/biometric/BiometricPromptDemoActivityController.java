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

package com.example.android.biometric;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

/**
 * Implementation of {@link com.example.android.biometric.BiometricPromptDemoController} that
 * handles launching the biometric prompt from within an activity host.
 */
class BiometricPromptDemoActivityController extends BiometricPromptDemoController {

    private final FragmentActivity mActivity;

    BiometricPromptDemoActivityController(
            @NonNull FragmentActivity activity,
            @NonNull View inflatedRootView) {
        super(inflatedRootView);
        mActivity = activity;
    }

    @Override
    Context getApplicationContext() {
        return mActivity.getApplicationContext();
    }

    @Override
    void reconnect() {
        mBiometricPrompt = new BiometricPrompt(mActivity, mExecutor, mAuthenticationCallback);
    }
}
