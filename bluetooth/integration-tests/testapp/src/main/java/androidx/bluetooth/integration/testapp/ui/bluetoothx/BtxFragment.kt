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

package androidx.bluetooth.integration.testapp.ui.bluetoothx

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.bluetooth.integration.testapp.R
import androidx.bluetooth.integration.testapp.databinding.FragmentBtxBinding
import androidx.bluetooth.integration.testapp.experimental.AdvertiseResult
import androidx.bluetooth.integration.testapp.experimental.BluetoothLe
import androidx.bluetooth.integration.testapp.experimental.GattServerCallback
import androidx.bluetooth.integration.testapp.ui.common.ScanResultAdapter
import androidx.bluetooth.integration.testapp.ui.framework.FwkFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class BtxFragment : Fragment() {

    companion object {
        const val TAG = "BtxFragment"
    }

    private var scanResultAdapter: ScanResultAdapter? = null

    private lateinit var bluetoothLe: BluetoothLe

    private lateinit var btxViewModel: BtxViewModel

    private var _binding: FragmentBtxBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(
            TAG, "onCreateView() called with: inflater = $inflater, " +
                "container = $container, savedInstanceState = $savedInstanceState"
        )
        btxViewModel = ViewModelProvider(this).get(BtxViewModel::class.java)

        _binding = FragmentBtxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothLe = BluetoothLe(requireContext())

        scanResultAdapter = ScanResultAdapter { scanResult -> scanResultOnClick(scanResult) }
        binding.recyclerView.adapter = scanResultAdapter

        binding.buttonScan.setOnClickListener {
            if (scanJob?.isActive == true) {
                scanJob?.cancel()
                binding.buttonScan.text = getString(R.string.scan_using_btx)
            } else {
                startScan()
            }
        }

        binding.switchAdvertise.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startAdvertise()
            else advertiseJob?.cancel()
        }

        binding.switchGattServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) openGattServer()
            else gattServerJob?.cancel()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scanJob?.cancel()
        advertiseJob?.cancel()
        gattServerJob?.cancel()
    }

    private val scanScope = CoroutineScope(Dispatchers.Main + Job())
    private var scanJob: Job? = null

    // Permissions are handled by MainActivity requestBluetoothPermissions
    @SuppressLint("MissingPermission")
    fun scan(settings: ScanSettings): Flow<ScanResult> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanFailed() called with: errorCode = $errorCode")
            }
        }

        val bluetoothManager =
            context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val bleScanner = bluetoothAdapter?.bluetoothLeScanner

        bleScanner?.startScan(null, settings, callback)

        awaitClose {
            Log.d(TAG, "awaitClose() called")
            bleScanner?.stopScan(callback)
        }
    }

    private fun startScan() {
        Log.d(TAG, "startScan() called")

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanJob = scanScope.launch {
            Toast.makeText(context, getString(R.string.scan_start_message), Toast.LENGTH_SHORT)
                .show()

            binding.buttonScan.text = getString(R.string.stop_scanning)

            scan(scanSettings)
                .collect {
                    Log.d(TAG, "ScanResult collected: $it")

                    btxViewModel.scanResults[it.device.address] = it
                    scanResultAdapter?.submitList(btxViewModel.scanResults.values.toMutableList())
                    scanResultAdapter?.notifyItemInserted(btxViewModel.scanResults.size)
                }
        }
    }

    private fun scanResultOnClick(scanResult: ScanResult) {
        Log.d(TAG, "scanResultOnClick() called with: scanResult = $scanResult")
    }

    private val advertiseScope = CoroutineScope(Dispatchers.Main + Job())
    private var advertiseJob: Job? = null

    // Permissions are handled by MainActivity requestBluetoothPermissions
    @SuppressLint("MissingPermission")
    private fun startAdvertise() {
        Log.d(TAG, "startAdvertise() called")

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTimeout(0)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(FwkFragment.ServiceUUID)
            .setIncludeDeviceName(true)
            .build()

        advertiseJob = advertiseScope.launch {
            bluetoothLe.advertise(advertiseSettings, advertiseData)
                .collect {
                    Log.d(TAG, "advertiseResult received: $it")

                    when (it) {
                        AdvertiseResult.ADVERTISE_STARTED -> {
                            Toast.makeText(
                                context,
                                getString(R.string.advertise_start_message), Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                        AdvertiseResult.ADVERTISE_FAILED_ALREADY_STARTED -> TODO()
                        AdvertiseResult.ADVERTISE_FAILED_DATA_TOO_LARGE -> TODO()
                        AdvertiseResult.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> TODO()
                        AdvertiseResult.ADVERTISE_FAILED_INTERNAL_ERROR -> TODO()
                        AdvertiseResult.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> TODO()
                    }
                }
        }
    }

    private val gattServerScope = CoroutineScope(Dispatchers.Main + Job())
    private var gattServerJob: Job? = null

    // Permissions are handled by MainActivity requestBluetoothPermissions
    @SuppressLint("MissingPermission")
    private fun openGattServer() {
        Log.d(TAG, "openGattServer() called")

        gattServerJob = gattServerScope.launch {
            bluetoothLe.gattServer().collect { gattServerCallback ->
                when (gattServerCallback) {
                    is GattServerCallback.OnCharacteristicReadRequest -> {
                        val onCharacteristicReadRequest:
                            GattServerCallback.OnCharacteristicReadRequest = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onCharacteristicReadRequest = $onCharacteristicReadRequest"
                        )
                    }
                    is GattServerCallback.OnCharacteristicWriteRequest -> {
                        val onCharacteristicWriteRequest:
                            GattServerCallback.OnCharacteristicWriteRequest = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onCharacteristicWriteRequest = $onCharacteristicWriteRequest"
                        )
                    }
                    is GattServerCallback.OnConnectionStateChange -> {
                        val onConnectionStateChange:
                            GattServerCallback.OnConnectionStateChange = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onConnectionStateChange = $onConnectionStateChange"
                        )
                    }
                    is GattServerCallback.OnDescriptorReadRequest -> {
                        val onDescriptorReadRequest:
                            GattServerCallback.OnDescriptorReadRequest = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onDescriptorReadRequest = $onDescriptorReadRequest"
                        )
                    }
                    is GattServerCallback.OnDescriptorWriteRequest -> {
                        val onDescriptorWriteRequest:
                            GattServerCallback.OnDescriptorWriteRequest = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onDescriptorWriteRequest = $onDescriptorWriteRequest"
                        )
                    }
                    is GattServerCallback.OnExecuteWrite -> {
                        val onExecuteWrite:
                            GattServerCallback.OnExecuteWrite = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onExecuteWrite = $onExecuteWrite"
                        )
                    }
                    is GattServerCallback.OnMtuChanged -> {
                        val onMtuChanged:
                            GattServerCallback.OnMtuChanged = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onMtuChanged = $onMtuChanged"
                        )
                    }
                    is GattServerCallback.OnNotificationSent -> {
                        val onNotificationSent:
                            GattServerCallback.OnNotificationSent = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onNotificationSent = $onNotificationSent"
                        )
                    }
                    is GattServerCallback.OnPhyRead -> {
                        val onPhyRead:
                            GattServerCallback.OnPhyRead = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onPhyRead = $onPhyRead"
                        )
                    }
                    is GattServerCallback.OnPhyUpdate -> {
                        val onPhyUpdate:
                            GattServerCallback.OnPhyUpdate = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onPhyUpdate = $onPhyUpdate"
                        )
                    }
                    is GattServerCallback.OnServiceAdded -> {
                        val onServiceAdded:
                            GattServerCallback.OnServiceAdded = gattServerCallback
                        Log.d(
                            TAG,
                            "openGattServer() called with: " +
                                "onServiceAdded = $onServiceAdded"
                        )
                    }
                }
            }
        }
    }
}
