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
package androidx.credentials.provider

import android.content.pm.SigningInfo
import android.service.credentials.CallingAppInfo
import androidx.annotation.RequiresApi
import androidx.core.os.BuildCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@RequiresApi(34)
class BeginCreatePublicKeyCredentialRequestTest {
    @Test
    fun constructor_emptyJson_throwsIllegalArgumentException() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        Assert.assertThrows(
            "Expected empty Json to throw error",
            IllegalArgumentException::class.java
        ) {
            BeginCreatePublicKeyCredentialRequest(
                "",
                CallingAppInfo(
                    "sample_package_name",
                    SigningInfo()
                )
            )
        }
    }

    @Test
    fun constructor_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        BeginCreatePublicKeyCredentialRequest(
            "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}",
            CallingAppInfo(
                "sample_package_name", SigningInfo()
            )
        )
    }

    @Test
    fun getter_requestJson_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        val testJsonExpected = "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}"

        val createPublicKeyCredentialReq = BeginCreatePublicKeyCredentialRequest(
            testJsonExpected,
            CallingAppInfo(
                "sample_package_name", SigningInfo()
            )
        )

        val testJsonActual = createPublicKeyCredentialReq.json
        assertThat(testJsonActual).isEqualTo(testJsonExpected)
    }
    // TODO ("Add framework conversion, createFrom & preferImmediatelyAvailable tests")
}