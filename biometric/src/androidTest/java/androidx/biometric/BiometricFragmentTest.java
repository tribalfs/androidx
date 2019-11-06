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

package androidx.biometric;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.DialogInterface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

@LargeTest
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
public class BiometricFragmentTest {
    private static final DialogInterface.OnClickListener CLICK_LISTENER =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            };

    private static final Executor EXECUTOR = new Executor() {
        @Override
        public void execute(@NonNull Runnable runnable) {
            runnable.run();
        }
    };

    @Test
    @UiThreadTest
    public void testOnAuthenticationSucceeded_TriggersCallbackWithNullCrypto_WhenGivenNullResult() {
        final BiometricFragment biometricFragment = BiometricFragment.newInstance();
        final BiometricPrompt.AuthenticationCallback callback =
                mock(BiometricPrompt.AuthenticationCallback.class);
        biometricFragment.setCallbacks(EXECUTOR, CLICK_LISTENER, callback);

        biometricFragment.mAuthenticationCallback.onAuthenticationSucceeded(null /* result */);

        final ArgumentCaptor<BiometricPrompt.AuthenticationResult> resultCaptor =
                ArgumentCaptor.forClass(BiometricPrompt.AuthenticationResult.class);
        verify(callback).onAuthenticationSucceeded(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getCryptoObject()).isNull();
    }

    @Test
    @UiThreadTest
    public void testIsDeviceCredentialAllowed_ReturnsFalse_WhenBundleIsNull() {
        final BiometricFragment biometricFragment = BiometricFragment.newInstance();
        biometricFragment.setBundle(null);
        assertThat(biometricFragment.isDeviceCredentialAllowed()).isFalse();
    }
}
