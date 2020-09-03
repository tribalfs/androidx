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

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class DeviceUtilsTest {
    @Mock private Context mContext;
    @Mock private Resources mResources;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testShouldUseFingerprintForCrypto() {
        final String[] vendors = {"hooli", "pied piper"};
        final String[] modelPrefixes = {"foo", "bar"};
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(R.array.crypto_fingerprint_fallback_vendors))
                .thenReturn(vendors);
        when(mResources.getStringArray(R.array.crypto_fingerprint_fallback_prefixes))
                .thenReturn(modelPrefixes);

        final boolean isApi28 = Build.VERSION.SDK_INT == Build.VERSION_CODES.P;
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "hooli", "foobar"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "hooli", "baz"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "hooli.xyz", "foobar"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "hooli.xyz", "baz"))
                .isFalse();
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "Pied Piper", "bar baz"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "Pied Piper", "qox"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "Aviato", "bar baz"))
                .isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldUseFingerprintForCrypto(mContext, "Aviato", "qox"))
                .isFalse();
    }

    @Test
    public void testShouldHideFingerprintDialog() {
        final String[] modelPrefixes = {"foo", "bar"};
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(R.array.hide_fingerprint_instantly_prefixes))
                .thenReturn(modelPrefixes);

        final boolean isApi28 = Build.VERSION.SDK_INT == Build.VERSION_CODES.P;
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "foo")).isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "bar")).isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "foobar")).isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "bar123")).isEqualTo(isApi28);
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "baz")).isFalse();
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "abcxyz")).isFalse();
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "bazfoo")).isFalse();
        assertThat(DeviceUtils.shouldHideFingerprintDialog(mContext, "FooBar")).isFalse();
    }

    @Test
    public void testShouldDelayShowingPrompt() {
        final String[] modelPrefixes = {"foo", "bar"};
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(R.array.delay_showing_prompt_prefixes))
                .thenReturn(modelPrefixes);

        final boolean isApi29 = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "foo")).isEqualTo(isApi29);
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "bar")).isEqualTo(isApi29);
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "foobar")).isEqualTo(isApi29);
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "bar123")).isEqualTo(isApi29);
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "baz")).isFalse();
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "abcxyz")).isFalse();
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "bazfoo")).isFalse();
        assertThat(DeviceUtils.shouldDelayShowingPrompt(mContext, "FooBar")).isFalse();
    }

    @Test
    public void testCanAssumeStrongBiometrics() {
        final String[] modelPrefixes = {"foo", "bar"};
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getStringArray(R.array.assume_strong_biometrics_prefixes))
                .thenReturn(modelPrefixes);

        final boolean isPreApi30 = Build.VERSION.SDK_INT < Build.VERSION_CODES.R;
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "foo")).isEqualTo(isPreApi30);
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "bar")).isEqualTo(isPreApi30);
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "foobar")).isEqualTo(isPreApi30);
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "bar123")).isEqualTo(isPreApi30);
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "baz")).isFalse();
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "abcxyz")).isFalse();
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "bazfoo")).isFalse();
        assertThat(DeviceUtils.canAssumeStrongBiometrics(mContext, "FooBar")).isFalse();
    }
}
