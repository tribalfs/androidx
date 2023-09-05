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

package androidx.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as FwkScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Entry point for BLE related operations. This class provides a way to perform Bluetooth LE
 * operations such as scanning, advertising, and connection with a respective [BluetoothDevice].
 */
class BluetoothLe constructor(private val context: Context) {

    private companion object {
        private const val TAG = "BluetoothLe"
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter = bluetoothManager?.adapter

    @VisibleForTesting
    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    val client: GattClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        GattClient(context)
    }
    @VisibleForTesting
    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    val server: GattServer by lazy(LazyThreadSafetyMode.PUBLICATION) {
        GattServer(context)
    }

    @VisibleForTesting
    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY)
    var onStartScanListener: OnStartScanListener? = null

    /**
     * Returns a _cold_ [Flow] to start Bluetooth LE Advertising.
     * When the flow is successfully collected, the operation status [AdvertiseResult] will be
     * delivered via the flow [kotlinx.coroutines.channels.Channel].
     *
     * @param advertiseParams [AdvertiseParams] for Bluetooth LE advertising
     * @return a _cold_ [Flow] with [AdvertiseResult] status in the data stream
     */
    @RequiresPermission("android.permission.BLUETOOTH_ADVERTISE")
    fun advertise(advertiseParams: AdvertiseParams): Flow<@AdvertiseResult.ResultType Int> =
        callbackFlow {
        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.d(TAG, "onStartFailure() called with: errorCode = $errorCode")

                when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE ->
                        trySend(AdvertiseResult.ADVERTISE_FAILED_DATA_TOO_LARGE)

                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                        trySend(AdvertiseResult.ADVERTISE_FAILED_FEATURE_UNSUPPORTED)

                    ADVERTISE_FAILED_INTERNAL_ERROR ->
                        trySend(AdvertiseResult.ADVERTISE_FAILED_INTERNAL_ERROR)

                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                        trySend(AdvertiseResult.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                }
            }

            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "onStartSuccess() called with: settingsInEffect = $settingsInEffect")

                trySend(AdvertiseResult.ADVERTISE_STARTED)
            }
        }

        val bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        val advertiseSettings = with(AdvertiseSettings.Builder()) {
            setConnectable(advertiseParams.isConnectable)
            setTimeout(advertiseParams.timeoutMillis)
            // TODO(b/290697177) Add when AndroidX is targeting Android U
//            setDiscoverable(advertiseParams.isDiscoverable)
            build()
        }

        val advertiseData = with(AdvertiseData.Builder()) {
            setIncludeDeviceName(advertiseParams.shouldIncludeDeviceName)
            advertiseParams.serviceData.forEach {
                addServiceData(ParcelUuid(it.key), it.value)
            }
            advertiseParams.manufacturerData.forEach {
                addManufacturerData(it.key, it.value)
            }
            advertiseParams.serviceUuids.forEach {
                addServiceUuid(ParcelUuid(it))
            }
            build()
        }

        Log.d(TAG, "bleAdvertiser.startAdvertising($advertiseSettings, $advertiseData) called")
        bleAdvertiser?.startAdvertising(advertiseSettings, advertiseData, callback)

        awaitClose {
            Log.d(TAG, "bleAdvertiser.stopAdvertising() called")
            bleAdvertiser?.stopAdvertising(callback)
        }
    }

    /**
     * Returns a _cold_ [Flow] to start Bluetooth LE scanning.
     * Scanning is used to discover advertising devices nearby.
     *
     * @param filters [ScanFilter]s for finding exact Bluetooth LE devices
     *
     * @return a _cold_ [Flow] of [ScanResult] that matches with the given scan filter
     */
    @RequiresPermission("android.permission.BLUETOOTH_SCAN")
    fun scan(filters: List<ScanFilter> = emptyList()): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: FwkScanResult) {
                trySend(ScanResult(result))
            }

            override fun onScanFailed(errorCode: Int) {
                // TODO(b/270492198): throw precise exception
                cancel("onScanFailed() called with: errorCode = $errorCode")
            }
        }

        val bleScanner = bluetoothAdapter?.bluetoothLeScanner
        val fwkFilters = filters.map { it.fwkScanFilter }
        val scanSettings = ScanSettings.Builder().build()
        bleScanner?.startScan(fwkFilters, scanSettings, callback)
        onStartScanListener?.onStartScan(bleScanner)

        awaitClose {
            bleScanner?.stopScan(callback)
        }
    }

    /**
     * Scope for operations as a GATT client role.
     *
     * @see BluetoothLe.connectGatt
     */
    interface GattClientScope {

        /**
         * Gets the services discovered from the remote device.
         */
        fun getServices(): List<GattService>

        /**
         * Gets the service of the remote device by UUID.
         *
         * If multiple instances of the same service exist, the first instance of the services
         * is returned.
         */
        fun getService(uuid: UUID): GattService?

        /**
         * Reads the characteristic value from the server.
         *
         * @param characteristic a remote [GattCharacteristic] to read
         * @return the value of the characteristic
         */
        suspend fun readCharacteristic(characteristic: GattCharacteristic):
            Result<ByteArray>

        /**
         * Writes the characteristic value to the server.
         *
         * @param characteristic a remote [GattCharacteristic] to write
         * @param value a value to be written.
         * @return the result of the write operation
         */
        suspend fun writeCharacteristic(
            characteristic: GattCharacteristic,
            value: ByteArray
        ): Result<Unit>

        /**
         * Returns a _cold_ [Flow] that contains the indicated value of the given characteristic.
         */
        fun subscribeToCharacteristic(characteristic: GattCharacteristic): Flow<ByteArray>

        /**
         * Suspends the current coroutine until the pending operations are handled and the
         * connection is closed, then it invokes the given [block] before resuming the coroutine.
         */
        suspend fun awaitClose(block: () -> Unit)
    }

    /**
     * Connects to the GATT server on the remote Bluetooth device and
     * invokes the given [block] after the connection is made.
     *
     * The block may not be run if connection fails.
     *
     * @param device a [BluetoothDevice] to connect to
     * @param block a block of code that is invoked after the connection is made
     *
     * @return a result returned by the given block if the connection was successfully finished
     *         or a failure with the corresponding reason
     *
     */
    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    suspend fun <R> connectGatt(
        device: BluetoothDevice,
        block: suspend GattClientScope.() -> R
    ): Result<R> {
        return client.connect(device, block)
    }

    /**
     * A scope for handling connect requests from remote devices.
     *
     * @property connectRequests connect requests from remote devices.
     *
     * @see BluetoothLe#openGattServer
     */
    interface GattServerConnectScope {
        /**
         * A _hot_ flow of [GattServerConnectRequest].
         */
        val connectRequests: Flow<GattServerConnectRequest>

        /**
         * Updates the services of the opened GATT server.
         *
         * @param services the new services that will be notified to the clients.
         */
        fun updateServices(services: List<GattService>)
    }

    /**
     * A scope for operations as a GATT server role.
     *
     * A scope is created for each remote device.
     *
     * Collect [requests] to respond with requests from the client.
     *
     * @see GattServerConnectRequest#accept()
     */
    interface GattServerSessionScope {
        /**
         * A client device connected to the server.
         */
        val device: BluetoothDevice

        /**
         * A _hot_ [Flow] of incoming requests from the client.
         *
         * A request is either [GattServerRequest.ReadCharacteristic] or
         * [GattServerRequest.WriteCharacteristic]
         */
        val requests: Flow<GattServerRequest>

        /**
         * Notifies a client of a characteristic value change.
         *
         * @param characteristic the updated characteristic
         * @param value the new value of the characteristic
         */
        fun notify(characteristic: GattCharacteristic, value: ByteArray)
    }

    /**
     * Represents a connect request from a remote device.
     *
     * @property device the remote device connecting to the server
     */
    class GattServerConnectRequest internal constructor(
        private val session: GattServer.Session,
    ) {
        val device: BluetoothDevice
            get() = session.device
        /**
         * Accepts the connect request and handles incoming requests after that.
         *
         * Requests from the client before calling this should be saved.
         *
         * @param block a block of code that is invoked after the connection is made.
         *
         * @see GattServerSessionScope
         */
        suspend fun accept(block: suspend GattServerSessionScope.() -> Unit) {
            return session.acceptConnection(block)
        }

        /**
         * Rejects the connect request.
         *
         * All the requests from the client will be rejected.
         */
        fun reject() {
            return session.rejectConnection()
        }
    }

    /**
     * Opens a GATT server.
     *
     *
     * Only one server at a time can be opened.
     *
     * @param services the services that will be exposed to the clients
     * @param block a block of code that is invoked after the server is opened
     *
     * @see GattServerConnectRequest
     */
    suspend fun <R> openGattServer(
        services: List<GattService>,
        block: suspend GattServerConnectScope.() -> R
    ): R {
        return server.open(services, block)
    }

    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun interface OnStartScanListener {
        fun onStartScan(scanner: BluetoothLeScanner?)
    }
}
