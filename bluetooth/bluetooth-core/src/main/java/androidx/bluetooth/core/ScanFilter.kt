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

package androidx.bluetooth.core

import android.bluetooth.le.ScanFilter as FwkScanFilter
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.bluetooth.core.utils.Bundleable
import androidx.bluetooth.core.utils.Utils

/**
 * TODO: Copy docs
 * @hide
 */
class ScanFilter internal constructor(
    internal val base: ScanFilterBase
) : ScanFilterInterface by base, Bundleable {
    companion object {
        internal const val FIELD_FWK_SCAN_FILTER = 0
        internal const val FIELD_SERVICE_SOLICITATION_UUID = 1
        internal const val FIELD_SERVICE_SOLICITATION_UUID_MASK = 2
        internal const val FIELD_ADVERTISING_DATA = 3
        internal const val FIELD_ADVERTISING_DATA_MASK = 4
        internal const val FIELD_ADVERTISING_DATA_TYPE = 5

        val CREATOR: Bundleable.Creator<ScanFilter> =
            object : Bundleable.Creator<ScanFilter> {
                override fun fromBundle(bundle: Bundle): ScanFilter {
                    return bundle.getScanFilter()
                }
            }

        internal fun keyForField(field: Int): String {
            return field.toString(Character.MAX_RADIX)
        }

        internal fun Bundle.putScanFilter(filter: ScanFilter) {
            this.putParcelable(
                keyForField(FIELD_FWK_SCAN_FILTER), filter.base.fwkInstance)
            if (Build.VERSION.SDK_INT < 29) {
                if (filter.serviceSolicitationUuid != null) {
                    this.putParcelable(
                        keyForField(FIELD_SERVICE_SOLICITATION_UUID),
                        filter.serviceSolicitationUuid)
                }
                if (filter.serviceSolicitationUuidMask != null) {
                    this.putParcelable(
                        keyForField(FIELD_SERVICE_SOLICITATION_UUID_MASK),
                        filter.serviceSolicitationUuidMask)
                }
            }
            if (Build.VERSION.SDK_INT < 33) {
                if (filter.advertisingData != null) {
                    this.putByteArray(
                        keyForField(FIELD_ADVERTISING_DATA),
                        filter.advertisingData)
                    this.putByteArray(
                        keyForField(FIELD_ADVERTISING_DATA_MASK),
                        filter.advertisingDataMask)
                    this.putInt(
                        keyForField(FIELD_ADVERTISING_DATA_TYPE),
                        filter.advertisingDataType)
                }
            }
        }

        internal fun Bundle.getScanFilter(): ScanFilter {
            val fwkScanFilter =
                Utils.getParcelableFromBundle(
                    this,
                    keyForField(FIELD_FWK_SCAN_FILTER),
                    android.bluetooth.le.ScanFilter::class.java
                ) ?: throw IllegalArgumentException(
                    "Bundle doesn't include a framework scan filter"
                )

            var args = ScanFilterArgs()
            if (Build.VERSION.SDK_INT < 33) {
                args.advertisingDataType = this.getInt(
                    keyForField(FIELD_ADVERTISING_DATA_TYPE), -1)
                args.advertisingData = this.getByteArray(
                    keyForField(FIELD_ADVERTISING_DATA))
                args.advertisingDataMask = this.getByteArray(
                    keyForField(FIELD_ADVERTISING_DATA_MASK))
            }
            if (Build.VERSION.SDK_INT < 29) {
                args.serviceSolicitationUuid =
                    Utils.getParcelableFromBundle(
                        this,
                        keyForField(FIELD_SERVICE_SOLICITATION_UUID),
                        ParcelUuid::class.java
                    )
                args.serviceSolicitationUuidMask =
                    Utils.getParcelableFromBundle(
                        this,
                        keyForField(FIELD_SERVICE_SOLICITATION_UUID_MASK),
                        ParcelUuid::class.java
                    )
            }
            return ScanFilter(fwkScanFilter, args)
        }
    }

    internal constructor(fwkInstance: FwkScanFilter) : this(
        if (Build.VERSION.SDK_INT >= 33) {
            ScanFilterImplApi33(fwkInstance)
        } else if (Build.VERSION.SDK_INT >= 29) {
            ScanFilterImplApi29(fwkInstance)
        } else {
            ScanFilterImplBase(fwkInstance)
        }
    )

    constructor(
        deviceName: String? = null,
        deviceAddress: String? = null,
        serviceUuid: ParcelUuid? = null,
        serviceUuidMask: ParcelUuid? = null,
        serviceDataUuid: ParcelUuid? = null,
        serviceData: ByteArray? = null,
        serviceDataMask: ByteArray? = null,
        manufacturerId: Int = -1,
        manufacturerData: ByteArray? = null,
        manufacturerDataMask: ByteArray? = null,
        serviceSolicitationUuid: ParcelUuid? = null,
        serviceSolicitationUuidMask: ParcelUuid? = null,
        advertisingData: ByteArray? = null,
        advertisingDataMask: ByteArray? = null,
        advertisingDataType: Int = -1 // TODO: Use ScanRecord.DATA_TYPE_NONE
    ) : this(ScanFilterArgs(
        deviceName,
        deviceAddress,
        serviceUuid,
        serviceUuidMask,
        serviceDataUuid,
        serviceData,
        serviceDataMask,
        manufacturerId,
        manufacturerData,
        manufacturerDataMask,
        serviceSolicitationUuid,
        serviceSolicitationUuidMask,
        advertisingData,
        advertisingDataMask,
        advertisingDataType
    ))

    internal constructor(args: ScanFilterArgs) : this(args.toFwkScanFilter(), args)

    internal constructor(fwkInstance: FwkScanFilter, args: ScanFilterArgs) : this(
        if (Build.VERSION.SDK_INT >= 33) {
            ScanFilterImplApi33(fwkInstance)
        } else if (Build.VERSION.SDK_INT >= 29) {
            ScanFilterImplApi29(fwkInstance, args)
        } else {
            ScanFilterImplBase(fwkInstance, args)
        }
    )
    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putScanFilter(this)
        return bundle
    }
}

internal data class ScanFilterArgs(
    var deviceName: String? = null,
    var deviceAddress: String? = null,
    var serviceUuid: ParcelUuid? = null,
    var serviceUuidMask: ParcelUuid? = null,
    var serviceDataUuid: ParcelUuid? = null,
    var serviceData: ByteArray? = null,
    var serviceDataMask: ByteArray? = null,
    var manufacturerId: Int = -1,
    var manufacturerData: ByteArray? = null,
    var manufacturerDataMask: ByteArray? = null,
    var serviceSolicitationUuid: ParcelUuid? = null,
    var serviceSolicitationUuidMask: ParcelUuid? = null,
    var advertisingData: ByteArray? = null,
    var advertisingDataMask: ByteArray? = null,
    var advertisingDataType: Int = -1 // TODO: Use ScanRecord.DATA_TYPE_NONE
) {
    // "ClassVerificationFailure" for FwkScanFilter.Builder
    // "WrongConstant" for AdvertisingDataType
    @SuppressLint("ClassVerificationFailure", "WrongConstant")
    internal fun toFwkScanFilter(): FwkScanFilter {
        val builder = FwkScanFilter.Builder()
            .setDeviceName(deviceName)
            .setDeviceAddress(deviceAddress)
            .setServiceUuid(serviceUuid)

        if (serviceDataUuid != null) {
            if (serviceDataMask == null) {
                builder.setServiceData(serviceDataUuid, serviceData)
            } else {
                builder.setServiceData(
                    serviceDataUuid, serviceData, serviceDataMask)
            }
        }
        if (manufacturerId >= 0) {
            if (manufacturerDataMask == null) {
                builder.setManufacturerData(manufacturerId, manufacturerData)
            } else {
                builder.setManufacturerData(
                    manufacturerId, manufacturerData, manufacturerDataMask)
            }
        }

        if (Build.VERSION.SDK_INT >= 29 && serviceSolicitationUuid != null) {
            if (serviceSolicitationUuidMask == null) {
                builder.setServiceSolicitationUuid(serviceSolicitationUuid)
            } else {
                builder.setServiceSolicitationUuid(
                    serviceSolicitationUuid, serviceSolicitationUuidMask
                )
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && advertisingDataType > 0) {
            if (advertisingData != null && advertisingDataMask != null) {
                builder.setAdvertisingDataTypeWithData(advertisingDataType,
                    advertisingData!!, advertisingDataMask!!)
            } else {
                builder.setAdvertisingDataType(advertisingDataType)
            }
        }

        return builder.build()
    }
}

internal interface ScanFilterInterface {
    val deviceName: String?
    val deviceAddress: String?
    val serviceUuid: ParcelUuid?
    val serviceUuidMask: ParcelUuid?
    val serviceDataUuid: ParcelUuid?
    val serviceData: ByteArray?
    val serviceDataMask: ByteArray?
    val manufacturerId: Int
    val manufacturerData: ByteArray?
    val manufacturerDataMask: ByteArray?
    val serviceSolicitationUuid: ParcelUuid?
    val serviceSolicitationUuidMask: ParcelUuid?
    val advertisingData: ByteArray?
    val advertisingDataMask: ByteArray?
    val advertisingDataType: Int
}

internal abstract class ScanFilterBase internal constructor(
    internal val fwkInstance: FwkScanFilter
) : ScanFilterInterface {
    override val deviceName: String?
        get() = fwkInstance.deviceName
    override val deviceAddress: String?
        get() = fwkInstance.deviceAddress
    override val serviceUuid: ParcelUuid?
        get() = fwkInstance.serviceUuid
    override val serviceUuidMask: ParcelUuid?
        get() = fwkInstance.serviceUuidMask
    override val serviceDataUuid: ParcelUuid?
        get() = fwkInstance.serviceDataUuid
    override val serviceData: ByteArray?
        get() = fwkInstance.serviceData
    override val serviceDataMask: ByteArray?
        get() = fwkInstance.serviceDataMask
    override val manufacturerId: Int
        get() = fwkInstance.manufacturerId
    override val manufacturerData: ByteArray?
        get() = fwkInstance.manufacturerData
    override val manufacturerDataMask: ByteArray?
        get() = fwkInstance.manufacturerDataMask
}

internal class ScanFilterImplBase internal constructor(
    fwkInstance: FwkScanFilter,
    override val serviceSolicitationUuid: ParcelUuid? = null,
    override val serviceSolicitationUuidMask: ParcelUuid? = null,
    override val advertisingData: ByteArray? = null,
    override val advertisingDataMask: ByteArray? = null,
    override val advertisingDataType: Int = -1 // TODO: Use ScanRecord.DATA_TYPE_NONE
) : ScanFilterBase(fwkInstance) {
    internal constructor(fwkInstance: FwkScanFilter, args: ScanFilterArgs) : this(
        fwkInstance, args.serviceSolicitationUuid, args.serviceSolicitationUuidMask,
        args.advertisingData, args.advertisingDataMask, args.advertisingDataType
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
internal abstract class ScanFilterBase29(fwkInstance: FwkScanFilter) : ScanFilterBase(fwkInstance) {
    override val serviceSolicitationUuid: ParcelUuid?
        get() = fwkInstance.serviceSolicitationUuid
    override val serviceSolicitationUuidMask: ParcelUuid?
        get() = fwkInstance.serviceSolicitationUuidMask
}

@RequiresApi(Build.VERSION_CODES.Q)
internal class ScanFilterImplApi29 internal constructor(
    fwkInstance: FwkScanFilter,
    override val advertisingData: ByteArray? = null,
    override val advertisingDataMask: ByteArray? = null,
    override val advertisingDataType: Int = -1 // TODO: Use ScanRecord.DATA_TYPE_NONE
) : ScanFilterBase29(fwkInstance) {
    internal constructor(fwkInstance: FwkScanFilter, args: ScanFilterArgs) : this(
        fwkInstance, args.advertisingData, args.advertisingDataMask, args.advertisingDataType
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal abstract class ScanFilterBase33(fwkInstance: FwkScanFilter) :
    ScanFilterBase29(fwkInstance) {
    override val advertisingData: ByteArray?
        get() = fwkInstance.advertisingData
    override val advertisingDataMask: ByteArray?
        get() = fwkInstance.advertisingDataMask
    override val advertisingDataType: Int
        get() = fwkInstance.advertisingDataType
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class ScanFilterImplApi33 internal constructor(
    fwkInstance: FwkScanFilter
) : ScanFilterBase33(fwkInstance)
