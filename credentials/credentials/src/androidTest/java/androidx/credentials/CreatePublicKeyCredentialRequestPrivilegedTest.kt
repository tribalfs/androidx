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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Combines with [CreatePublicKeyCredentialRequestPrivilegedFailureInputsTest] for full tests.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class CreatePublicKeyCredentialRequestPrivilegedTest {

    @Test
    fun constructor_success() {
        CreatePublicKeyCredentialRequestPrivileged(
            "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}",
            "RP", "ClientDataHash"
        )
    }

    @Test
    fun constructor_setsAllowHybridToTrueByDefault() {
        val createPublicKeyCredentialRequestPrivileged = CreatePublicKeyCredentialRequestPrivileged(
            "JSON", "RP", "HASH"
        )
        val allowHybridActual = createPublicKeyCredentialRequestPrivileged.allowHybrid
        Truth.assertThat(allowHybridActual).isTrue()
    }

    @Test
    fun constructor_setsAllowHybridToFalse() {
        val allowHybridExpected = false
        val createPublicKeyCredentialRequestPrivileged = CreatePublicKeyCredentialRequestPrivileged(
            "testJson",
            "RP", "Hash", allowHybridExpected
        )
        val allowHybridActual = createPublicKeyCredentialRequestPrivileged.allowHybrid
        Truth.assertThat(allowHybridActual).isEqualTo(allowHybridExpected)
    }

    @Test
    fun builder_build_defaultAllowHybrid_true() {
        val defaultPrivilegedRequest = CreatePublicKeyCredentialRequestPrivileged.Builder(
            "{\"Data\":5}",
            "RP", "HASH"
        ).build()
        Truth.assertThat(defaultPrivilegedRequest.allowHybrid).isTrue()
    }

    @Test
    fun builder_build_nonDefaultAllowHybrid_false() {
        val allowHybridExpected = false
        val createPublicKeyCredentialRequestPrivileged = CreatePublicKeyCredentialRequestPrivileged
            .Builder(
                "testJson",
                "RP", "Hash"
            ).setAllowHybrid(allowHybridExpected).build()
        val allowHybridActual = createPublicKeyCredentialRequestPrivileged.allowHybrid
        Truth.assertThat(allowHybridActual).isEqualTo(allowHybridExpected)
    }

    @Test
    fun getter_requestJson_success() {
        val testJsonExpected = "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}"
        val createPublicKeyCredentialReqPriv =
            CreatePublicKeyCredentialRequestPrivileged(testJsonExpected, "RP", "HASH")
        val testJsonActual = createPublicKeyCredentialReqPriv.requestJson
        Truth.assertThat(testJsonActual).isEqualTo(testJsonExpected)
    }

    @Test
    fun getter_rp_success() {
        val testRpExpected = "RP"
        val createPublicKeyCredentialRequestPrivileged =
            CreatePublicKeyCredentialRequestPrivileged(
                "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}",
                testRpExpected, "X342%4dfd7&"
            )
        val testRpActual = createPublicKeyCredentialRequestPrivileged.rp
        Truth.assertThat(testRpActual).isEqualTo(testRpExpected)
    }

    @Test
    fun getter_clientDataHash_success() {
        val clientDataHashExpected = "X342%4dfd7&"
        val createPublicKeyCredentialRequestPrivileged =
            CreatePublicKeyCredentialRequestPrivileged(
                "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}",
                "RP", clientDataHashExpected
            )
        val clientDataHashActual = createPublicKeyCredentialRequestPrivileged.clientDataHash
        Truth.assertThat(clientDataHashActual).isEqualTo(clientDataHashExpected)
    }
}