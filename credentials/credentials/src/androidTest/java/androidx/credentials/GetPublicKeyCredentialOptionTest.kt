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
import androidx.credentials.GetCredentialOption.Companion.createFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class GetPublicKeyCredentialOptionTest {

    @Test
    fun constructor_emptyJson_throwsIllegalArgumentException() {
        Assert.assertThrows(
            "Expected empty Json to throw error",
            IllegalArgumentException::class.java
        ) { GetPublicKeyCredentialOption("") }
    }

    @Test
    fun constructor_success() {
        GetPublicKeyCredentialOption(
            "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}"
        )
    }

    @Test
    fun constructor_setsAllowHybridToTrueByDefault() {
        val getPublicKeyCredentialOpt = GetPublicKeyCredentialOption(
            "JSON"
        )
        val allowHybridActual = getPublicKeyCredentialOpt.allowHybrid
        assertThat(allowHybridActual).isTrue()
    }

    @Test
    fun constructor_setsAllowHybridFalse() {
        val allowHybridExpected = false
        val getPublicKeyCredentialOpt = GetPublicKeyCredentialOption(
            "JSON", allowHybridExpected
        )
        val allowHybridActual = getPublicKeyCredentialOpt.allowHybrid
        assertThat(allowHybridActual).isEqualTo(allowHybridExpected)
    }

    @Test
    fun getter_requestJson_success() {
        val testJsonExpected = "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}"
        val createPublicKeyCredentialReq = GetPublicKeyCredentialOption(testJsonExpected)
        val testJsonActual = createPublicKeyCredentialReq.requestJson
        assertThat(testJsonActual).isEqualTo(testJsonExpected)
    }

    @Test
    fun getter_frameworkProperties_success() {
        val requestJsonExpected = "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}"
        val allowHybridExpected = false
        val expectedData = Bundle()
        expectedData.putString(
            PublicKeyCredential.BUNDLE_KEY_SUBTYPE,
            GetPublicKeyCredentialOption.BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION
        )
        expectedData.putString(
            GetPublicKeyCredentialOption.BUNDLE_KEY_REQUEST_JSON,
            requestJsonExpected
        )
        expectedData.putBoolean(
            GetPublicKeyCredentialOption.BUNDLE_KEY_ALLOW_HYBRID,
            allowHybridExpected
        )

        val option = GetPublicKeyCredentialOption(requestJsonExpected, allowHybridExpected)

        assertThat(option.type).isEqualTo(PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL)
        assertThat(equals(option.data, expectedData)).isTrue()
        assertThat(option.requireSystemProvider).isFalse()
    }

    @Test
    fun frameworkConversion_success() {
        val option = GetPublicKeyCredentialOption("json", true)

        val convertedOption = createFrom(
            option.type, option.data, option.requireSystemProvider
        )

        assertThat(convertedOption).isInstanceOf(
            GetPublicKeyCredentialOption::class.java
        )
        val convertedSubclassOption = convertedOption as GetPublicKeyCredentialOption
        assertThat(convertedSubclassOption.requestJson).isEqualTo(option.requestJson)
        assertThat(convertedSubclassOption.allowHybrid).isEqualTo(option.allowHybrid)
    }
}