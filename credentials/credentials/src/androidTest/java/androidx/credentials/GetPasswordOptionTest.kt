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

package androidx.credentials

import android.os.Bundle
import androidx.credentials.CredentialOption.Companion.createFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class GetPasswordOptionTest {
    @Test
    fun getter_frameworkProperties() {
        val option = GetPasswordOption()
        val expectedRequestDataBundle = Bundle()
        expectedRequestDataBundle.putBoolean(
            CredentialOption.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED,
            false
        )

        assertThat(option.type).isEqualTo(PasswordCredential.TYPE_PASSWORD_CREDENTIAL)
        assertThat(equals(option.requestData, expectedRequestDataBundle)).isTrue()
        assertThat(equals(option.candidateQueryData, Bundle.EMPTY)).isTrue()
        assertThat(option.isSystemProviderRequired).isFalse()
    }

    @Test
    fun frameworkConversion_success() {
        val option = GetPasswordOption()

        val convertedOption = createFrom(
            option.type,
            option.requestData,
            option.candidateQueryData,
            option.isSystemProviderRequired
        )

        assertThat(convertedOption).isInstanceOf(GetPasswordOption::class.java)
    }
}