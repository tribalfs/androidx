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

package androidx.health.connect.client.impl

import android.content.Context
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.PackageInfoFlags
import android.healthconnect.ChangeLogsRequest
import android.healthconnect.HealthConnectException
import android.healthconnect.HealthConnectManager
import android.healthconnect.ReadRecordsRequestUsingIds
import android.healthconnect.RecordIdFilter
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.core.os.asOutcomeReceiver
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.impl.platform.records.toPlatformChangeLogTokenRequest
import androidx.health.connect.client.impl.platform.records.toPlatformReadRecordsRequestUsingFilters
import androidx.health.connect.client.impl.platform.records.toPlatformRecord
import androidx.health.connect.client.impl.platform.records.toPlatformRecordClass
import androidx.health.connect.client.impl.platform.records.toPlatformTimeRangeFilter
import androidx.health.connect.client.impl.platform.records.toSdkRecord
import androidx.health.connect.client.impl.platform.response.toKtResponse
import androidx.health.connect.client.impl.platform.time.SystemDefaultTimeSource
import androidx.health.connect.client.impl.platform.time.TimeSource
import androidx.health.connect.client.impl.platform.toKtException
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_PREFIX
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Implements the [HealthConnectClient] with APIs in UpsideDownCake.
 *
 * @suppress
 */
@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class HealthConnectClientUpsideDownImpl : HealthConnectClient, PermissionController {

    private val context: Context
    private val timeSource: TimeSource
    private val healthConnectManager: HealthConnectManager

    constructor(context: Context) : this(context, SystemDefaultTimeSource)

    internal constructor(context: Context, timeSource: TimeSource) {
        this.context = context
        this.timeSource = timeSource
        this.healthConnectManager =
            context.getSystemService(Context.HEALTHCONNECT_SERVICE) as HealthConnectManager
    }

    override val permissionController: PermissionController
        get() = this

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
        val response = wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.insertRecords(
                    records.map { it.toPlatformRecord() },
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
        return response.toKtResponse()
    }

    override suspend fun updateRecords(records: List<Record>) {
        wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.updateRecords(
                    records.map { it.toPlatformRecord() },
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
    }

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>
    ) {
        wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.deleteRecords(
                    buildList {
                        recordIdsList.forEach {
                            add(
                                RecordIdFilter.Builder(recordType.toPlatformRecordClass())
                                    .setId(it)
                                    .build()
                            )
                        }
                        clientRecordIdsList.forEach {
                            add(
                                RecordIdFilter.Builder(recordType.toPlatformRecordClass())
                                    .setClientRecordId(it)
                                    .build()
                            )
                        }
                    },
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
    }

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        timeRangeFilter: TimeRangeFilter
    ) {
        wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.deleteRecords(
                    recordType.toPlatformRecordClass(),
                    timeRangeFilter.toPlatformTimeRangeFilter(timeSource),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST") // Safe to cast as the type should match
    override suspend fun <T : Record> readRecord(
        recordType: KClass<T>,
        recordId: String
    ): ReadRecordResponse<T> {
        val response = wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.readRecords(
                    ReadRecordsRequestUsingIds.Builder(recordType.toPlatformRecordClass())
                        .addId(recordId)
                        .build(),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
        if (response.records.isEmpty()) {
            throw RemoteException("No records")
        }
        return ReadRecordResponse(response.records[0].toSdkRecord() as T)
    }

    @Suppress("UNCHECKED_CAST") // Safe to cast as the type should match
    override suspend fun <T : Record> readRecords(
        request: ReadRecordsRequest<T>
    ): ReadRecordsResponse<T> {
        val response = wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.readRecords(
                    request.toPlatformReadRecordsRequestUsingFilters(timeSource),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
        // TODO(b/262573513): pass page token
        return ReadRecordsResponse(response.records.map { it.toSdkRecord() as T }, null)
    }

    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest
    ): List<AggregationResultGroupedByDuration> {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest
    ): List<AggregationResultGroupedByPeriod> {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
        return wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.getChangeLogToken(
                    request.toPlatformChangeLogTokenRequest(),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
    }

    override suspend fun registerForDataNotifications(
        notificationIntentAction: String,
        recordTypes: Iterable<KClass<out Record>>
    ) {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun unregisterFromDataNotifications(notificationIntentAction: String) {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun getChanges(changesToken: String): ChangesResponse {
        val response = wrapPlatformException {
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.getChangeLogs(
                    ChangeLogsRequest.Builder(changesToken).build(),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }
        // TODO(b/263472286) revisit changesTokenExpired field in the constructor
        return ChangesResponse(
            buildList {
                response.upsertedRecords.forEach { add(UpsertionChange(it.toSdkRecord())) }
                response.deletedRecordIds.forEach { add(DeletionChange(it)) }
            },
            response.nextChangesToken,
            response.hasMorePages(),
            changesTokenExpired = true
        )
    }

    override suspend fun getGrantedPermissionsLegacy(
        permissions: Set<HealthPermission>
    ): Set<HealthPermission> {
        throw UnsupportedOperationException("Method not supported yet")
    }

    override suspend fun getGrantedPermissions(): Set<String> {
        context.packageManager
            .getPackageInfo(context.packageName, PackageInfoFlags.of(GET_PERMISSIONS.toLong()))
            .let {
                return buildSet {
                    for (i in it.requestedPermissions.indices) {
                        if (
                            it.requestedPermissions[i].startsWith(PERMISSION_PREFIX) &&
                                it.requestedPermissionsFlags[i] and REQUESTED_PERMISSION_GRANTED > 0
                        ) {
                            add(it.requestedPermissions[i])
                        }
                    }
                }
            }
    }

    override suspend fun revokeAllPermissions() {
        throw UnsupportedOperationException("Method not supported yet")
    }

    private suspend fun <T> wrapPlatformException(function: suspend () -> T): T {
        return try {
            function()
        } catch (e: HealthConnectException) {
            throw e.toKtException()
        }
    }
}
