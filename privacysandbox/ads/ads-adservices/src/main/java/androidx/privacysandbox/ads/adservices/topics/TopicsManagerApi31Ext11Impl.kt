/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.privacysandbox.ads.adservices.topics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.privacysandbox.ads.adservices.common.ExperimentalFeatures

@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint("NewApi")
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 11)
class TopicsManagerApi31Ext11Impl(context: Context) :
    TopicsManagerImplCommon(
        android.adservices.topics.TopicsManager.get(context),
    ) {
    override fun convertRequest(
        request: GetTopicsRequest
    ): android.adservices.topics.GetTopicsRequest {
        return GetTopicsRequestHelper.convertRequestWithRecordObservation(request)
    }

    @ExperimentalFeatures.Ext11OptIn
    override fun convertResponse(
        response: android.adservices.topics.GetTopicsResponse
    ): GetTopicsResponse {
        return GetTopicsResponseHelper.convertResponseWithEncryptedTopics(response)
    }
}
