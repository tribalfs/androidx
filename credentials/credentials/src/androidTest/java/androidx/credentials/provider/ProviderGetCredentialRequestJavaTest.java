/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.credentials.provider;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.service.credentials.CallingAppInfo;

import androidx.core.os.BuildCompat;
import androidx.credentials.CredentialOption;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@SdkSuppress(minSdkVersion = 34, codeName = "UpsideDownCake")
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProviderGetCredentialRequestJavaTest {

    @Test
    public void constructor_success() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }

        new ProviderGetCredentialRequest(
                Collections.singletonList(CredentialOption.createFrom("type", new Bundle(),
                        new Bundle(), true)), new CallingAppInfo("name",
                new SigningInfo()));
    }

    @Test
    public void constructor_nullInputs_throws() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }

        assertThrows("Expected null list to throw NPE",
                NullPointerException.class,
                () -> new ProviderGetCredentialRequest(null, null)
        );
    }

    @Test
    public void getter_credentialOptions() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        String expectedType = "BoeingCred";
        String expectedQueryKey = "PilotName";
        String expectedQueryValue = "PilotPassword";
        Bundle expectedCandidateQueryData = new Bundle();
        expectedCandidateQueryData.putString(expectedQueryKey, expectedQueryValue);
        String expectedRequestKey = "PlaneKey";
        String expectedRequestValue = "PlaneInfo";
        Bundle expectedRequestData = new Bundle();
        expectedRequestData.putString(expectedRequestKey, expectedRequestValue);
        boolean expectedRequireSystemProvider = true;

        ProviderGetCredentialRequest providerGetCredentialRequest =
                new ProviderGetCredentialRequest(
                        Collections.singletonList(CredentialOption.createFrom(expectedType,
                                expectedRequestData,
                                expectedCandidateQueryData,
                                expectedRequireSystemProvider)),
                        new CallingAppInfo("name",
                                new SigningInfo()));
        List<CredentialOption> actualCredentialOptionsList =
                providerGetCredentialRequest.getCredentialOptions();
        assertThat(actualCredentialOptionsList.size()).isEqualTo(1);
        String actualType = actualCredentialOptionsList.get(0).getType();
        String actualRequestValue =
                actualCredentialOptionsList.get(0).getRequestData().getString(expectedRequestKey);
        String actualQueryValue =
                actualCredentialOptionsList.get(0).getCandidateQueryData()
                        .getString(expectedQueryKey);
        boolean actualRequireSystemProvider =
                actualCredentialOptionsList.get(0).isSystemProviderRequired();

        assertThat(actualType).isEqualTo(expectedType);
        assertThat(actualRequestValue).isEqualTo(expectedRequestValue);
        assertThat(actualQueryValue).isEqualTo(expectedQueryValue);
        assertThat(actualRequireSystemProvider).isEqualTo(expectedRequireSystemProvider);
    }

    @Test
    public void getter_signingInfo() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        String expectedPackageName = "cool.security.package";

        ProviderGetCredentialRequest providerGetCredentialRequest =
                new ProviderGetCredentialRequest(
                        Collections.singletonList(CredentialOption.createFrom("type", new Bundle(),
                                new Bundle(), true)), new CallingAppInfo(expectedPackageName,
                        new SigningInfo()));
        String actualPackageName =
                providerGetCredentialRequest.getCallingAppInfo().getPackageName();

        assertThat(actualPackageName).isEqualTo(expectedPackageName);
    }
}
