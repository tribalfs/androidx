/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.platform.client.response

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsertDataResponseTest {

    @Test
    fun writeToParcel() {
        val insertDataResponse = InsertDataResponse(dataPointUids = listOf("uid1", "uid2", "uid3"))

        val parcel: Parcel = Parcel.obtain()
        parcel.writeParcelable(insertDataResponse, 0)
        parcel.setDataPosition(0)
        @Suppress("Deprecation") // readParcelable deprecated in T and introduced new methods
        val out: InsertDataResponse? =
            parcel.readParcelable(InsertDataResponse::class.java.classLoader)
        Truth.assertThat(out?.dataPointUids).isEqualTo(insertDataResponse.dataPointUids)
    }
}
