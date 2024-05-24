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

package androidx.bluetooth.utils

import android.bluetooth.BluetoothDevice as FwkBluetoothDevice
import androidx.annotation.RestrictTo
import java.security.MessageDigest
import java.util.UUID
import kotlin.experimental.and
import kotlin.experimental.or

@RestrictTo(RestrictTo.Scope.LIBRARY)
internal fun deviceId(packageName: String, fwkDevice: FwkBluetoothDevice): UUID {
    val name = packageName + fwkDevice.address + fwkDevice.addressType()
    val md = MessageDigest.getInstance("SHA-1")
    md.update(name.toByteArray())
    val hash = md.digest()

    // Set to version 5
    hash[6] = hash[6] and (0x0F).toByte()
    hash[6] = hash[6] or (0x50).toByte()
    // Set to IETF variant
    hash[8] = hash[8] and (0x3F).toByte()
    hash[8] = hash[8] or (0x80).toByte()

    var msb: Long = 0
    var lsb: Long = 0

    for (i in 0..7) {
        msb = (msb.shl(8) or (hash[i].toLong() and 0xFF))
    }
    for (i in 8..15) {
        lsb = (lsb.shl(8) or (hash[i].toLong() and 0xFF))
    }

    return UUID(msb, lsb)
}
