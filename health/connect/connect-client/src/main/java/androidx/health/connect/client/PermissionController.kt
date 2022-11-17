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
package androidx.health.connect.client

import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RestrictTo
import androidx.health.connect.client.HealthConnectClient.Companion.DEFAULT_PROVIDER_PACKAGE_NAME
import androidx.health.connect.client.permission.HealthDataRequestPermissions
import androidx.health.connect.client.permission.HealthDataRequestPermissionsInternal
import androidx.health.connect.client.permission.HealthPermission

@JvmDefaultWithCompatibility
/** Interface for operations related to permissions. */
interface PermissionController {

    /**
     * Returns a set of [HealthPermission] granted by the user to the calling app, out of the input
     * [permissions] set.
     *
     * @param permissions set of permissions interested to check if granted or not
     * @return set of granted permissions.
     *
     * @throws android.os.RemoteException For any IPC transportation failures.
     * @throws java.io.IOException For any disk I/O issues.
     * @throws IllegalStateException If service is not available.
     */
    suspend fun getGrantedPermissions(permissions: Set<HealthPermission>): Set<HealthPermission>

    /**
     * Filters and returns a subset of permissions granted by the user to the calling app, out of
     * the input permissions set.
     *
     * @param permissions set of permissions to filter. Each permission should be one of the list
     * defined in [HealthPermission]
     * @return filtered set of granted permissions.
     *
     * @throws android.os.RemoteException For any IPC transportation failures.
     * @throws java.io.IOException For any disk I/O issues.
     * @throws IllegalStateException If service is not available.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY) // Not yet ready for public
    suspend fun filterGrantedPermissions(permissions: Set<String>): Set<String>

    /**
     * Revokes all previously granted [HealthPermission] by the user to the calling app.
     *
     * @throws android.os.RemoteException For any IPC transportation failures.
     * @throws java.io.IOException For any disk I/O issues.
     * @throws IllegalStateException If service is not available.
     */
    suspend fun revokeAllPermissions()

    companion object {
        /**
         * Creates an [ActivityResultContract] to request Health permissions.
         *
         * @param providerPackageName Optional provider package name to request health permissions
         * from.
         *
         * @see androidx.activity.ComponentActivity.registerForActivityResult
         * @sample androidx.health.connect.client.samples.RequestPermission
         */
        @JvmStatic
        @JvmOverloads
        fun createRequestPermissionResultContract(
            providerPackageName: String = DEFAULT_PROVIDER_PACKAGE_NAME
        ): ActivityResultContract<Set<HealthPermission>, Set<HealthPermission>> {
            return HealthDataRequestPermissions(providerPackageName = providerPackageName)
        }

        /**
         * Creates an [ActivityResultContract] to request Health permissions.
         *
         * @param providerPackageName Optional provider package name to request health permissions
         * from.
         *
         * @see androidx.activity.ComponentActivity.registerForActivityResult
         * @sample androidx.health.connect.client.samples.RequestPermission
         */
        @JvmStatic
        @JvmOverloads
        @RestrictTo(RestrictTo.Scope.LIBRARY) // Not yet ready for public
        fun createRequestPermissionResultContractInternal(
            providerPackageName: String = DEFAULT_PROVIDER_PACKAGE_NAME
        ): ActivityResultContract<Set<String>, Set<String>> {
            return HealthDataRequestPermissionsInternal(providerPackageName = providerPackageName)
        }
    }
}
