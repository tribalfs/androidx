/*
 * Copyright (C) 2021 The Android Open Source Project
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

package androidx.health.services.client.impl.response

import android.os.Parcelable
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.ProtoParcelable
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.proto.ResponsesProto

/** Response sent on MeasureCallback when new (non-aggregate) [DataPoint]s are available. */
internal class DataPointsResponse(public val dataPoints: List<DataPoint<*>>) :
    ProtoParcelable<ResponsesProto.DataPointsResponse>() {

    public constructor(
        proto: ResponsesProto.DataPointsResponse
    ) : this(proto.dataPointsList.map { DataPoint.fromProto(it) })

    override val proto: ResponsesProto.DataPointsResponse =
        ResponsesProto.DataPointsResponse.newBuilder()
            .addAllDataPoints(
                dataPoints
                    .filter { it is IntervalDataPoint || it is SampleDataPoint }
                    .map {
                        when (it) {
                            is IntervalDataPoint -> it.proto
                            is SampleDataPoint -> it.proto
                            else -> throw IllegalStateException("Invalid DataPoint type: $it")
                        }
                    }
            )
            .build()

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<DataPointsResponse> = newCreator { bytes ->
            val proto = ResponsesProto.DataPointsResponse.parseFrom(bytes)
            DataPointsResponse(proto)
        }
    }
}
