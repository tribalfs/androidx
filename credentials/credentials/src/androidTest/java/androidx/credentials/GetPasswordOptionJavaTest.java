/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.credentials;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class GetPasswordOptionJavaTest {
    @Test
    public void emptyConstructor_success() {
        GetPasswordOption option = new GetPasswordOption();

        assertThat(option.isAutoSelectAllowed()).isFalse();
    }

    @Test
    public void construction_setOptionalValues_success() {
        boolean expectedIsAutoSelectAllowed = true;

        GetPasswordOption option = new GetPasswordOption(expectedIsAutoSelectAllowed);

        assertThat(option.isAutoSelectAllowed()).isEqualTo(expectedIsAutoSelectAllowed);
    }

    @Test
    public void getter_frameworkProperties() {
        GetPasswordOption option = new GetPasswordOption();
        Bundle expectedRequestDataBundle = new Bundle();
        expectedRequestDataBundle.putBoolean(GetPasswordOption.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED,
                false);

        assertThat(option.getType()).isEqualTo(PasswordCredential.TYPE_PASSWORD_CREDENTIAL);
        assertThat(TestUtilsKt.equals(option.getRequestData(), expectedRequestDataBundle)).isTrue();
        assertThat(TestUtilsKt.equals(option.getCandidateQueryData(),
                expectedRequestDataBundle)).isTrue();
        assertThat(option.isSystemProviderRequired()).isFalse();
    }

    @Test
    public void frameworkConversion_success() {
        boolean expectedIsAutoSelectAllowed = true;
        GetPasswordOption option = new GetPasswordOption(expectedIsAutoSelectAllowed);

        CredentialOption convertedOption = CredentialOption.createFrom(
                option.getType(), option.getRequestData(), option.getCandidateQueryData(),
                option.isSystemProviderRequired());

        assertThat(convertedOption).isInstanceOf(GetPasswordOption.class);
        GetPasswordOption getPasswordOption = (GetPasswordOption) convertedOption;
        assertThat(getPasswordOption.isAutoSelectAllowed()).isEqualTo(expectedIsAutoSelectAllowed);
    }
}
