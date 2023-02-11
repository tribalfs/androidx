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

package androidx.appactions.interaction.service

import java.util.Objects

/**
 * Representation of a client's verified package with a corresponding list of Sha256 signatures.
 * Used as part of the {@link SecurityPolicy} creation for the grpc service.
 */
class AppVerificationInfo
internal constructor(val packageName: String, val signatures: List<ByteArray>) {

    override fun toString() =
        "AppVerificationInfo(packageName=$packageName, signaturesSize=$signatures.size)"

    override fun equals(other: Any?): Boolean {
        return other is AppVerificationInfo &&
            packageName == other.packageName &&
            signatures == other.signatures
    }

    override fun hashCode() = Objects.hash(packageName, signatures)

    /** Builder for constructing an AppVerificationInfo instance. */
    class Builder {
        private var packageName: String? = null
        private var signatures: List<ByteArray> = emptyList()

        /** Set the packageName that will be part of the [AppVerificationInfo] */
        fun setPackageName(packageName: String) = apply { this.packageName = packageName }

        /** Set the packageName that will be part of the [AppVerificationInfo] */
        fun addSignature(signatures: List<ByteArray>) = apply { this.signatures = signatures }

        /**
         * Creates a new instance of [AppVerificationInfo]
         *
         * @Throws IllegalArgumentException if packageName is null or signatures are empty.
         */
        fun build(): AppVerificationInfo {
            if (packageName == null) {
                throw IllegalArgumentException("App verification info packageName is missing.")
            }
            if (signatures.isEmpty()) {
                throw IllegalArgumentException("App verification info signatures are missing.")
            }
            return AppVerificationInfo(packageName!!, signatures)
        }
    }
}
