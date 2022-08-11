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
@file:RestrictTo(RestrictTo.Scope.LIBRARY)

package androidx.health.connect.client.impl.converters.request

import androidx.annotation.RestrictTo
import androidx.health.connect.client.impl.converters.aggregate.toProto
import androidx.health.connect.client.impl.converters.time.toProto
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.platform.client.proto.DataProto
import androidx.health.platform.client.proto.RequestProto

fun AggregateRequest.toProto(): RequestProto.AggregateDataRequest =
    RequestProto.AggregateDataRequest.newBuilder()
        .setTimeSpec(timeRangeFilter.toProto())
        .addAllDataOrigin(dataOriginFilter.toProtoList())
        .addAllMetricSpec(metrics.map { it.toProto() })
        .build()

@SuppressWarnings("NewApi")
fun AggregateGroupByDurationRequest.toProto(): RequestProto.AggregateDataRequest =
    RequestProto.AggregateDataRequest.newBuilder()
        .setTimeSpec(timeRangeFilter.toProto())
        .addAllDataOrigin(dataOriginFilter.toProtoList())
        .addAllMetricSpec(metrics.map { it.toProto() })
        .setSliceDurationMillis(timeRangeSlicer.toMillis())
        .build()

@SuppressWarnings("NewApi")
fun AggregateGroupByPeriodRequest.toProto(): RequestProto.AggregateDataRequest =
    RequestProto.AggregateDataRequest.newBuilder()
        .setTimeSpec(timeRangeFilter.toProto())
        .addAllDataOrigin(dataOriginFilter.toProtoList())
        .addAllMetricSpec(metrics.map { it.toProto() })
        .setSlicePeriod(timeRangeSlicer.toString())
        .build()

private fun Set<DataOrigin>.toProtoList() =
    this.map { DataProto.DataOrigin.newBuilder().setApplicationId(it.packageName).build() }
