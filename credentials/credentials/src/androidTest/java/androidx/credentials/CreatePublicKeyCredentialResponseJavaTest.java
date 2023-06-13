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

import static org.junit.Assert.assertThrows;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CreatePublicKeyCredentialResponseJavaTest {
    private static final String TEST_RESPONSE_JSON = "{\"hi\":{\"there\":{\"lol\":\"Value\"}}}";

    @Test
    public void constructor_emptyJson_throwsIllegalArgumentException() {
        assertThrows("Expected empty Json to throw error",
                IllegalArgumentException.class,
                () -> new CreatePublicKeyCredentialResponse("")
        );
    }

    @Test
    public void constructor_nullJson_throwsNullPointerException() {
        assertThrows("Expected null Json to throw NullPointerException",
                NullPointerException.class,
                () -> new CreatePublicKeyCredentialResponse(null)
        );
    }

    @Test
    public void constructor_success()  {
        new CreatePublicKeyCredentialResponse(TEST_RESPONSE_JSON);
    }

    @Test
    public void getter_registrationResponseJson_success() {
        String testJsonExpected = "{\"input\":5}";
        CreatePublicKeyCredentialResponse createPublicKeyCredentialResponse =
                new CreatePublicKeyCredentialResponse(testJsonExpected);
        String testJsonActual = createPublicKeyCredentialResponse.getRegistrationResponseJson();
        assertThat(testJsonActual).isEqualTo(testJsonExpected);
    }

    @Test
    public void getter_frameworkProperties_success() {
        String registrationResponseJsonExpected = "{\"input\":5}";
        Bundle expectedData = new Bundle();
        expectedData.putString(
                CreatePublicKeyCredentialResponse.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON,
                registrationResponseJsonExpected);

        CreatePublicKeyCredentialResponse response =
                new CreatePublicKeyCredentialResponse(registrationResponseJsonExpected);

        assertThat(response.getType()).isEqualTo(PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL);
        assertThat(TestUtilsKt.equals(response.getData(), expectedData)).isTrue();
    }

    @Test
    public void frameworkConversion_success() {
        CreatePublicKeyCredentialResponse response =
                new CreatePublicKeyCredentialResponse(TEST_RESPONSE_JSON);
        // Add additional data to the request data and candidate query data to make sure
        // they persist after the conversion
        Bundle data = response.getData();
        String customDataKey = "customRequestDataKey";
        CharSequence customDataValue = "customRequestDataValue";
        data.putCharSequence(customDataKey, customDataValue);

        CreateCredentialResponse convertedResponse =
                CreateCredentialResponse.createFrom(response.getType(), data);

        assertThat(convertedResponse).isInstanceOf(CreatePublicKeyCredentialResponse.class);
        CreatePublicKeyCredentialResponse convertedSubclassResponse =
                (CreatePublicKeyCredentialResponse) convertedResponse;
        assertThat(convertedSubclassResponse.getRegistrationResponseJson())
                .isEqualTo(response.getRegistrationResponseJson());
        assertThat(convertedResponse.getData().getCharSequence(customDataKey))
                .isEqualTo(customDataValue);
    }
}
