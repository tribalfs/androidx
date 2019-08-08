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
import androidx.fragment.app.Fragment;

/**
 * Implementation of {@link com.example.android.biometric.BiometricPromptDemoController} that
 * handles launching the biometric prompt from within a fragment host.
 */
class BiometricPromptDemoFragmentController extends BiometricPromptDemoController {

    private final Context mContext;
    private final Fragment mFragment;

    BiometricPromptDemoFragmentController(
            @NonNull Context context,
            @NonNull Fragment fragment,
            @NonNull View inflatedRootView) {
        super(inflatedRootView);
        mContext = context;
        mFragment = fragment;
    }

    @Override
    Context getApplicationContext() {
        return mContext.getApplicationContext();
    }

    @Override
    void reconnect() {
        mBiometricPrompt = new BiometricPrompt(mFragment, mExecutor, mAuthenticationCallback);
    }
}
