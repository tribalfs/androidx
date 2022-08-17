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

package androidx.health.connect.client.impl.converters.records

import androidx.annotation.RestrictTo
import androidx.health.connect.client.records.InstantaneousRecord
import androidx.health.connect.client.records.IntervalRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.platform.client.proto.DataProto
import java.time.Instant

internal fun protoDataType(dataTypeName: String): DataProto.DataType =
    DataProto.DataType.newBuilder().setName(dataTypeName).build()

@SuppressWarnings("GoodTime") // Suppress GoodTime for serialize/de-serialize.
internal fun InstantaneousRecord.instantaneousProto(): DataProto.DataPoint.Builder {
    val builder =
        DataProto.DataPoint.newBuilder()
            .setMetadata(metadata)
            .setInstantTimeMillis(time.toEpochMilli())
    zoneOffset?.let { builder.setZoneOffsetSeconds(it.totalSeconds) }
    return builder
}

@SuppressWarnings("GoodTime") // Suppress GoodTime for serialize/de-serialize.
internal fun IntervalRecord.intervalProto(): DataProto.DataPoint.Builder {
    val builder =
        DataProto.DataPoint.newBuilder()
            .setMetadata(metadata)
            .setStartTimeMillis(startTime.toEpochMilli())
            .setEndTimeMillis(endTime.toEpochMilli())
    startZoneOffset?.let { builder.setStartZoneOffsetSeconds(it.totalSeconds) }
    endZoneOffset?.let { builder.setEndZoneOffsetSeconds(it.totalSeconds) }
    return builder
}

@SuppressWarnings("GoodTime") // Suppress GoodTime for serialize/de-serialize.
private fun DataProto.DataPoint.Builder.setMetadata(metadata: Metadata) = apply {
    if (metadata.uid != Metadata.EMPTY_UID) {
        setUid(metadata.uid)
    }
    if (metadata.dataOrigin.packageName.isNotEmpty()) {
        setDataOrigin(
            DataProto.DataOrigin.newBuilder()
                .setApplicationId(metadata.dataOrigin.packageName)
                .build()
        )
    }

    if (metadata.lastModifiedTime.isAfter(Instant.EPOCH)) {
        setUpdateTimeMillis(metadata.lastModifiedTime.toEpochMilli())
    }

    metadata.clientRecordId?.let { setClientId(it) }
    if (metadata.clientRecordVersion > 0) {
        metadata.clientRecordVersion.let { setClientVersion(it) }
    }
    metadata.device?.let { setDevice(it.toProto()) }
}

private fun Device.toProto(): DataProto.Device {
    val obj = this
    return DataProto.Device.newBuilder()
        .apply {
            obj.manufacturer?.let { setManufacturer(it) }
            obj.model?.let { setModel(it) }
            obj.type?.let { setType(it) }
        }
        .build()
}
