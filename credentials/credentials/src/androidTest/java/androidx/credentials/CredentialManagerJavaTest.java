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

import static androidx.credentials.TestUtilsKt.isPostFrameworkApiLevel;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.ClearCredentialProviderConfigurationException;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class CredentialManagerJavaTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private CredentialManager mCredentialManager;

    @Before
    public void setup() {
        mCredentialManager = CredentialManager.create(mContext);
    }

    @Test
    public void testCreateCredentialAsyc_successCallbackThrows() throws InterruptedException {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CreateCredentialException> loadedResult = new AtomicReference<>();
        ActivityScenario<TestActivity> activityScenario =
                ActivityScenario.launch(TestActivity.class);
        activityScenario.onActivity(activity -> {
            mCredentialManager.createCredentialAsync(
                    new CreatePasswordRequest("test-user-id", "test-password"),
                    activity,
                    null,
                    Runnable::run,
                    new CredentialManagerCallback<CreateCredentialResponse,
                            CreateCredentialException>() {
                        @Override
                        public void onError(@NonNull CreateCredentialException e) {
                            loadedResult.set(e);
                            latch.countDown();
                        }

                        @Override
                        public void onResult(@NonNull CreateCredentialResponse result) {}
                    });
        });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                CreateCredentialProviderConfigurationException.class);
        // TODO("Add manifest tests and possibly further separate these tests by API Level
        //  - maybe a rule perhaps?")
    }


    @Test
    public void testGetCredentialAsyc_successCallbackThrows() throws InterruptedException {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GetCredentialException> loadedResult = new AtomicReference<>();

        mCredentialManager.getCredentialAsync(
                new GetCredentialRequest.Builder()
                        .addCredentialOption(new GetPasswordOption())
                        .build(),
                new Activity(),
                null,
                Runnable::run,
                new CredentialManagerCallback<GetCredentialResponse,
                    GetCredentialException>() {
                @Override
                public void onError(@NonNull GetCredentialException e) {
                    loadedResult.set(e);
                    latch.countDown();
                }

                @Override
                public void onResult(@NonNull GetCredentialResponse result) {}
            });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                GetCredentialProviderConfigurationException.class);
        // TODO("Add manifest tests and possibly further separate these tests - maybe a rule
        //  perhaps?")
    }

    @Test
    public void testClearCredentialSessionAsync_throws() throws InterruptedException {
        if (isPostFrameworkApiLevel()) {
            return; // TODO(Support!)
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ClearCredentialException> loadedResult = new AtomicReference<>();

        mCredentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                null,
                Runnable::run,
                new CredentialManagerCallback<Void,
                        ClearCredentialException>() {
                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        loadedResult.set(e);
                        latch.countDown();
                    }

                    @Override
                    public void onResult(@NonNull Void result) {}
                });

        latch.await(100L, TimeUnit.MILLISECONDS);
        assertThat(loadedResult.get().getClass()).isEqualTo(
                ClearCredentialProviderConfigurationException.class);
        // TODO(Add manifest tests and split this once postU is implemented for clearCreds")
    }
}
