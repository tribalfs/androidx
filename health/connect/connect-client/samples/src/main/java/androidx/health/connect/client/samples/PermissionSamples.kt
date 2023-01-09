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

@file:Suppress("UNUSED_VARIABLE")

package androidx.health.connect.client.samples

import androidx.activity.result.ActivityResultCaller
import androidx.annotation.Sampled
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

@Sampled
fun RequestPermission(activity: ActivityResultCaller) {
    val requestPermission =
        activity.registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { grantedPermissions: Set<String> ->
            if (
                grantedPermissions.contains(HealthPermission.getReadPermission(StepsRecord::class))
            ) {
                // Read or process steps related health records.
            } else {
                // user denied permission
            }
        }
    requestPermission.launch(setOf(HealthPermission.getReadPermission(StepsRecord::class)))
}

@Sampled
suspend fun GetPermissions(permissionController: PermissionController) {
    val grantedPermissions = permissionController.getGrantedPermissions()

    if (grantedPermissions.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
        // Read or process steps related health records.
    } else {
        // user denied permission
    }
}
