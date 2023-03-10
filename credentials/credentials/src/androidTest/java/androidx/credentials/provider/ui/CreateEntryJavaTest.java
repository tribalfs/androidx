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

package androidx.credentials.provider.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;

import androidx.core.os.BuildCompat;
import androidx.credentials.provider.CreateEntry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
@SmallTest
@SdkSuppress(minSdkVersion = 34, codeName = "UpsideDownCake")
public class CreateEntryJavaTest {
    private static final CharSequence ACCOUNT_NAME = "account_name";
    private static final int PASSWORD_COUNT = 10;
    private static final int PUBLIC_KEY_CREDENTIAL_COUNT = 10;
    private static final int TOTAL_COUNT = 10;

    private static final Long LAST_USED_TIME = 10L;
    private static final Icon ICON = Icon.createWithBitmap(Bitmap.createBitmap(
            100, 100, Bitmap.Config.ARGB_8888));

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Intent mIntent = new Intent();
    private final PendingIntent mPendingIntent =
            PendingIntent.getActivity(mContext, 0, mIntent,
                    PendingIntent.FLAG_IMMUTABLE);

    @Test
    public void constructor_requiredParameters_success() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        CreateEntry entry = constructEntryWithRequiredParams();

        assertNotNull(entry);
        assertNotNull(entry.getSlice());
        assertEntryWithRequiredParams(entry);
        assertNull(entry.getIcon());
        assertNull(entry.getLastUsedTime());
        assertNull(entry.getPasswordCredentialCount());
        assertNull(entry.getPublicKeyCredentialCount());
        assertNull(entry.getTotalCredentialCount());
    }

    @Test
    public void constructor_allParameters_success() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        CreateEntry entry = constructEntryWithAllParams();

        assertNotNull(entry);
        assertNotNull(entry.getSlice());
        assertEntryWithAllParams(entry);
    }

    @Test
    public void constructor_nullAccountName_throwsNPE() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        assertThrows("Expected null title to throw NPE",
                NullPointerException.class,
                () -> new CreateEntry.Builder(
                        null, mPendingIntent).build());
    }

    @Test
    public void constructor_nullPendingIntent_throwsNPE() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        assertThrows("Expected null pending intent to throw NPE",
                NullPointerException.class,
                () -> new CreateEntry.Builder(ACCOUNT_NAME, null).build());
    }

    @Test
    public void constructor_emptyAccountName_throwsIAE() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        assertThrows("Expected empty account name to throw NPE",
                IllegalArgumentException.class,
                () -> new CreateEntry.Builder("", mPendingIntent).build());
    }

    @Test
    public void fromSlice_requiredParams_success() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        CreateEntry originalEntry = constructEntryWithRequiredParams();

        CreateEntry entry = CreateEntry.fromSlice(
                originalEntry.getSlice());

        assertNotNull(entry);
        assertEntryWithRequiredParams(entry);
    }

    @Test
    public void fromSlice_allParams_success() {
        if (!BuildCompat.isAtLeastU()) {
            return;
        }
        CreateEntry originalEntry = constructEntryWithAllParams();

        CreateEntry entry = CreateEntry.fromSlice(
                originalEntry.getSlice());

        assertNotNull(entry);
        assertEntryWithAllParams(entry);
    }

    private CreateEntry constructEntryWithRequiredParams() {
        return new CreateEntry.Builder(ACCOUNT_NAME, mPendingIntent).build();
    }

    private void assertEntryWithRequiredParams(CreateEntry entry) {
        assertThat(ACCOUNT_NAME.equals(entry.getAccountName()));
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
    }

    private CreateEntry constructEntryWithAllParams() {
        return new CreateEntry.Builder(
                ACCOUNT_NAME,
                mPendingIntent)
                .setIcon(ICON)
                .setLastUsedTime(Instant.ofEpochMilli(LAST_USED_TIME))
                .setPasswordCredentialCount(PASSWORD_COUNT)
                .setPublicKeyCredentialCount(PUBLIC_KEY_CREDENTIAL_COUNT)
                .setTotalCredentialCount(TOTAL_COUNT)
                .build();
    }

    private void assertEntryWithAllParams(CreateEntry entry) {
        assertThat(ACCOUNT_NAME).isEqualTo(entry.getAccountName());
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
        assertThat(ICON).isEqualTo(entry.getIcon());
        assertThat(Instant.ofEpochMilli(LAST_USED_TIME)).isEqualTo(entry.getLastUsedTime());
        assertThat(PASSWORD_COUNT).isEqualTo(entry.getPasswordCredentialCount());
        assertThat(PUBLIC_KEY_CREDENTIAL_COUNT).isEqualTo(entry.getPublicKeyCredentialCount());
        assertThat(TOTAL_COUNT).isEqualTo(entry.getTotalCredentialCount());
    }
}
