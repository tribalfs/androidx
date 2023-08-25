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

import android.content.Intent;

import androidx.annotation.RequiresApi;
import androidx.credentials.CreatePasswordResponse;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PasswordCredential;
import androidx.credentials.exceptions.CreateCredentialInterruptedException;
import androidx.credentials.exceptions.GetCredentialInterruptedException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresApi(34)
@RunWith(AndroidJUnit4.class)
@SmallTest
@SdkSuppress(minSdkVersion = 34, codeName = "UpsideDownCake")
public class PendingIntentHandlerJavaTest {
    private static final Intent BLANK_INTENT = new Intent();

    @Test
    public void test_setGetCreateCredentialException() {
        Intent intent = new Intent();

        CreateCredentialInterruptedException initialException =
                new CreateCredentialInterruptedException("message");

        PendingIntentHandler.setCreateCredentialException(intent, initialException);

        android.credentials.CreateCredentialException finalException =
                IntentHandlerConverters.getCreateCredentialException(intent);
        assertThat(finalException).isNotNull();
        assertThat(finalException.getMessage()).isEqualTo(initialException.getMessage());
    }

    @Test
    public void test_setGetCreateCredentialException_throwsWhenEmptyIntent() {
        assertThat(
                        IntentHandlerConverters.getCreateCredentialException(
                                BLANK_INTENT))
                .isNull();
    }

    @Test
    public void test_credentialException() {
        Intent intent = new Intent();
        GetCredentialInterruptedException initialException =
                new GetCredentialInterruptedException("message");

        PendingIntentHandler.setGetCredentialException(intent, initialException);

        android.credentials.GetCredentialException finalException =
                IntentHandlerConverters.getGetCredentialException(intent);
        assertThat(finalException).isNotNull();
        assertThat(finalException).isEqualTo(initialException);
    }

    @Test
    public void test_credentialException_throwsWhenEmptyIntent() {
        assertThat(IntentHandlerConverters.getGetCredentialException(BLANK_INTENT))
                .isNull();
    }

    @Test
    public void test_beginGetResponse() {

        Intent intent = new Intent();
        BeginGetCredentialResponse initialResponse =
                new BeginGetCredentialResponse.Builder().build();

        PendingIntentHandler.setBeginGetCredentialResponse(intent, initialResponse);

        BeginGetCredentialResponse finalResponse =
                IntentHandlerConverters.getBeginGetResponse(intent);
        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse).isEqualTo(initialResponse);
    }

    @Test
    public void test_beginGetResponse_throwsWhenEmptyIntent() {
        assertThat(IntentHandlerConverters.getBeginGetResponse(BLANK_INTENT))
                .isNull();
    }

    @Test
    public void test_credentialResponse() {
        Intent intent = new Intent();
        PasswordCredential credential = new PasswordCredential("a", "b");
        GetCredentialResponse initialResponse = new GetCredentialResponse(credential);

        PendingIntentHandler.setGetCredentialResponse(intent, initialResponse);

        android.credentials.GetCredentialResponse finalResponse =
                IntentHandlerConverters.getGetCredentialResponse(intent);
        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse).isEqualTo(initialResponse);
    }

    @Test
    public void test_credentialResponse_throwsWhenEmptyIntent() {
        assertThat(IntentHandlerConverters.getGetCredentialResponse(BLANK_INTENT))
                .isNull();
    }

    @Test
    public void test_createCredentialCredentialResponse() {
        Intent intent = new Intent();
        CreatePasswordResponse initialResponse = new CreatePasswordResponse();

        PendingIntentHandler.setCreateCredentialResponse(intent, initialResponse);

        android.credentials.CreateCredentialResponse finalResponse =
                IntentHandlerConverters.getCreateCredentialCredentialResponse(
                        intent);
        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse).isEqualTo(initialResponse);
    }

    @Test
    public void test_createCredentialCredentialResponse_throwsWhenEmptyIntent() {
        assertThat(
                        IntentHandlerConverters
                                .getCreateCredentialCredentialResponse(BLANK_INTENT))
                .isNull();
    }
}
