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

import android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGatt.GATT_WRITE_NOT_PERMITTED
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a request to be handled as a GATT server role.
 *
 * @see BluetoothLe.GattServerConnectRequest.accept
 */
open class GattServerRequest private constructor() {
    private val handled = AtomicBoolean(false)

    internal inline fun handleRequest(block: () -> Unit) {
        if (handled.compareAndSet(false, true)) {
            block()
        } else {
            throw IllegalStateException("Request is already handled")
        }
    }

    /**
     * Represents a read characteristic request.
     *
     * @property characteristic a characteristic to read
     */
    class ReadCharacteristic internal constructor(
        private val session: GattServer.Session,
        private val requestId: Int,
        private val offset: Int,
        val characteristic: GattCharacteristic
    ) : GattServerRequest() {
        /**
         * Sends the result for the read request.
         *
         * @param value a value of the characteristic
         */
        fun sendResponse(value: ByteArray) {
            handleRequest {
                val resValue: ByteArray = if (offset == 0) value
                else if (value.size > offset) value.copyOfRange(offset, value.size - 1)
                else if (value.size == offset) byteArrayOf()
                else byteArrayOf()
                session.sendResponse(requestId, GATT_SUCCESS, offset, resValue)
            }
        }

        /**
         * Notifies the failure for the read request.
         */
        fun sendFailure() {
            handleRequest {
                session.sendResponse(requestId, GATT_READ_NOT_PERMITTED, offset, null)
            }
        }
    }

    /**
     * Represents a write characteristic request.
     *
     * @property characteristic a characteristic to write
     * @property value a value to write
     */
    class WriteCharacteristic internal constructor(
        private val session: GattServer.Session,
        private val requestId: Int,
        val characteristic: GattCharacteristic,
        val value: ByteArray?
    ) : GattServerRequest() {
        /**
         * Sends the result for the write request.
         *
         * @param value an optional value that is written
         */
        fun sendResponse(value: ByteArray?) {
            handleRequest {
                session.sendResponse(requestId, GATT_SUCCESS, 0, value)
            }
        }

        /**
         * Notifies the failure for the write request.
         */
        fun sendFailure() {
            handleRequest {
                session.sendResponse(requestId, GATT_WRITE_NOT_PERMITTED, 0, null)
            }
        }
    }
}
