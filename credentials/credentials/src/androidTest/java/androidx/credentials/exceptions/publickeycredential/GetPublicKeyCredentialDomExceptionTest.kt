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

package androidx.credentials.exceptions.publickeycredential

import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.EncodingError
import com.google.common.truth.Truth
import org.junit.Test

class GetPublicKeyCredentialDomExceptionTest {
    @Test(expected = GetPublicKeyCredentialDomException::class)
    fun construct_inputNonEmpty_success() {
        throw GetPublicKeyCredentialDomException(
            AbortError(), "msg"
        )
    }

    @Test
    fun getter_success() {
        val expectedMessage = "msg"
        val expectedDomError = EncodingError()
        val expectedType =
            GetPublicKeyCredentialDomException.TYPE_GET_PUBLIC_KEY_CREDENTIAL_DOM_EXCEPTION +
                expectedDomError.type

        val exception = GetPublicKeyCredentialDomException(expectedDomError, expectedMessage)

        Truth.assertThat(exception.type).isEqualTo(expectedType)
        Truth.assertThat(exception.errorMessage).isEqualTo(expectedMessage)
    }
}