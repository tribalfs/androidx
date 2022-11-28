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
import androidx.credentials.CreateCredentialResponse.Companion.createFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreatePasswordResponseTest {
    @Test
    fun getter_frameworkProperties() {
        val response = CreatePasswordResponse()

        assertThat(response.type).isEqualTo(PasswordCredential.TYPE_PASSWORD_CREDENTIAL)

        assertThat(equals(response.data, Bundle.EMPTY)).isTrue()
    }

    @Test
    fun frameworkConversion_success() {
        val response = CreatePasswordResponse()

        val convertedResponse = createFrom(response.type, response.data)

        assertThat(convertedResponse).isInstanceOf(CreatePasswordResponse::class.java)
    }
}