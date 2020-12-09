/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface.client

/** Describes the hardware configuration of the device the watch face is running on. */
public class DeviceConfig(
    /** Whether or not the watch hardware supports low bit ambient support. */
    @get:JvmName("hasLowBitAmbient")
    public val hasLowBitAmbient: Boolean,

    /** Whether or not the watch hardware supports burn in protection. */
    @get:JvmName("hasBurnInProtection")
    public val hasBurnInProtection: Boolean,

    /**
     * UTC reference time for screenshots of analog watch faces in milliseconds since the epoch.
     */
    public val analogPreviewReferenceTimeMillis: Long,

    /**
     * UTC reference time for screenshots of digital watch faces in milliseconds since the epoch.
     */
    public val digitalPreviewReferenceTimeMillis: Long
)
