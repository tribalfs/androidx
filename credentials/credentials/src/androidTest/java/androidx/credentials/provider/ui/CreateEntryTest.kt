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
package androidx.credentials.provider.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.os.BuildCompat
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CreateEntry.Companion.fromSlice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import java.time.Instant
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 34, codeName = "UpsideDownCake")
@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateEntryTest {
    private val mContext = ApplicationProvider.getApplicationContext<Context>()
    private val mIntent = Intent()
    private val mPendingIntent = PendingIntent.getActivity(
        mContext, 0, mIntent,
        PendingIntent.FLAG_IMMUTABLE
    )

    @Test
    fun constructor_requiredParameters_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        val entry = constructEntryWithRequiredParams()

        assertNotNull(entry)
        assertNotNull(entry.slice)
        assertEntryWithRequiredParams(entry)
        assertNull(entry.icon)
        assertNull(entry.lastUsedTime)
        assertNull(entry.getPasswordCredentialCount())
        assertNull(entry.getPublicKeyCredentialCount())
        assertNull(entry.getTotalCredentialCount())
    }

    @Test
    fun constructor_allParameters_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        val entry = constructEntryWithAllParams()

        assertNotNull(entry)
        assertNotNull(entry.slice)
        assertEntryWithAllParams(entry)
    }

    @Test
    fun constructor_emptyAccountName_throwsIAE() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        Assert.assertThrows(
            "Expected empty account name to throw NPE",
            IllegalArgumentException::class.java
        ) {
            CreateEntry(
                "", mPendingIntent
            )
        }
    }

    @Test
    fun fromSlice_requiredParams_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        val originalEntry = constructEntryWithRequiredParams()

        val entry = fromSlice(originalEntry.slice)

        assertNotNull(entry)
        entry?.let {
            assertEntryWithRequiredParams(entry)
        }
    }

    @Test
    fun fromSlice_allParams_success() {
        if (!BuildCompat.isAtLeastU()) {
            return
        }
        val originalEntry = constructEntryWithAllParams()

        val entry = fromSlice(originalEntry.slice)

        assertNotNull(entry)
        entry?.let {
            assertEntryWithAllParams(entry)
        }
    }

    private fun constructEntryWithRequiredParams(): CreateEntry {
        return CreateEntry(
            ACCOUNT_NAME,
            mPendingIntent
        )
    }

    private fun assertEntryWithRequiredParams(entry: CreateEntry) {
        Truth.assertThat(ACCOUNT_NAME == entry.accountName)
        Truth.assertThat(mPendingIntent).isEqualTo(entry.pendingIntent)
    }

    private fun constructEntryWithAllParams(): CreateEntry {
        return CreateEntry(
            ACCOUNT_NAME,
            mPendingIntent,
            DESCRIPTION,
            Instant.ofEpochMilli(LAST_USED_TIME),
            ICON,
            PASSWORD_COUNT,
            PUBLIC_KEY_CREDENTIAL_COUNT,
            TOTAL_COUNT
        )
    }

    private fun assertEntryWithAllParams(entry: CreateEntry) {
        Truth.assertThat(ACCOUNT_NAME).isEqualTo(
            entry.accountName
        )
        Truth.assertThat(mPendingIntent).isEqualTo(entry.pendingIntent)
        Truth.assertThat(ICON).isEqualTo(
            entry.icon
        )
        Truth.assertThat(LAST_USED_TIME).isEqualTo(
            entry.lastUsedTime?.toEpochMilli()
        )
        Truth.assertThat(PASSWORD_COUNT).isEqualTo(
            entry.getPasswordCredentialCount()
        )
        Truth.assertThat(PUBLIC_KEY_CREDENTIAL_COUNT).isEqualTo(
            entry.getPublicKeyCredentialCount()
        )
        Truth.assertThat(TOTAL_COUNT).isEqualTo(
            entry.getTotalCredentialCount()
        )
    }

    companion object {
        private val ACCOUNT_NAME: CharSequence = "account_name"
        private const val DESCRIPTION = "description"
        private const val PASSWORD_COUNT = 10
        private const val PUBLIC_KEY_CREDENTIAL_COUNT = 10
        private const val TOTAL_COUNT = 10
        private const val LAST_USED_TIME = 10L
        private val ICON = Icon.createWithBitmap(
            Bitmap.createBitmap(
                100, 100, Bitmap.Config.ARGB_8888
            )
        )
        private const val IS_AUTO_SELECT_ALLOWED = true
    }
}