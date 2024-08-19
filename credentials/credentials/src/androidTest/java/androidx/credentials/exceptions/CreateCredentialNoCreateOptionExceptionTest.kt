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

package androidx.credentials.exceptions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateCredentialNoCreateOptionExceptionTest {
    @Test(expected = CreateCredentialNoCreateOptionException::class)
    @Throws(CreateCredentialNoCreateOptionException::class)
    fun construct_inputNonEmpty_success() {
        throw CreateCredentialNoCreateOptionException("msg")
    }

    @Test(expected = CreateCredentialNoCreateOptionException::class)
    @Throws(CreateCredentialNoCreateOptionException::class)
    fun construct_errorMessageNull_success() {
        throw CreateCredentialNoCreateOptionException(null)
    }

    @Test
    fun getter_success() {
        val expectedClass = CreateCredentialNoCreateOptionException::class.java
        val expectedMessage = "message"
        val exception = CreateCredentialNoCreateOptionException(expectedMessage)
        assertThat(exception.javaClass).isEqualTo(expectedClass)
        assertThat(exception.errorMessage).isEqualTo(expectedMessage)
        assertThat(exception.type)
            .isEqualTo(
                CreateCredentialNoCreateOptionException.TYPE_CREATE_CREDENTIAL_NO_CREATE_OPTION
            )
    }

    @Test
    fun bundleConversion_withMessage_success() {
        val expectedClass = CreateCredentialNoCreateOptionException::class.java
        val expectedMessage = "message"
        val exception = CreateCredentialNoCreateOptionException(expectedMessage)

        val actual =
            CreateCredentialException.fromBundle(CreateCredentialException.asBundle(exception))

        assertThat(actual).isInstanceOf(expectedClass)
        assertThat(actual.errorMessage).isEqualTo(expectedMessage)
    }

    @Test
    fun bundleConversion_withoutMessage_success() {
        val expectedClass = CreateCredentialNoCreateOptionException::class.java
        val expectedMessage = null
        val exception = CreateCredentialNoCreateOptionException(expectedMessage)

        val actual =
            CreateCredentialException.fromBundle(CreateCredentialException.asBundle(exception))

        assertThat(actual).isInstanceOf(expectedClass)
        assertThat(actual.errorMessage).isEqualTo(expectedMessage)
    }
}
