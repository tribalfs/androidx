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

package androidx.health.services.client.impl.request

import android.os.Parcelable
import androidx.annotation.RestrictTo
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.ProtoParcelable
import androidx.health.services.client.proto.RequestsProto

/**
 * Request for updating batching mode of an exercise.
 *
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BatchingModeConfigRequest(
    public val packageName: String,
    public val batchingModeOverrides: Set<BatchingMode>,
) : ProtoParcelable<RequestsProto.BatchingModeConfigRequest>() {

    override val proto: RequestsProto.BatchingModeConfigRequest =
        RequestsProto.BatchingModeConfigRequest.newBuilder()
            .setPackageName(packageName)
            .addAllBatchingModeOverrides(batchingModeOverrides.map { it.toProto() })
            .build()

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<BatchingModeConfigRequest> = newCreator {
            val request = RequestsProto.BatchingModeConfigRequest.parseFrom(it)
            BatchingModeConfigRequest(
                request.packageName,
                request.batchingModeOverridesList.map { BatchingMode(it) }.toSet(),
            )
        }
    }
}
