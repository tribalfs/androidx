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

package androidx.privacysandbox.ads.adservices.adselection

import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.privacysandbox.ads.adservices.common.AdTechIdentifier
import androidx.privacysandbox.ads.adservices.common.ExperimentalFeatures

/**
 * Represent input parameters to the [AdSelectionManager#getAdSelectionData] API.
 *
 * @param seller AdTechIdentifier of the seller, for example "www.example-ssp.com".
 */
@ExperimentalFeatures.Ext10OptIn
class GetAdSelectionDataRequest public constructor(
    val seller: AdTechIdentifier? = null,
) {
    /** Checks whether two [GetAdSelectionDataRequest] objects contain the same information.  */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetAdSelectionDataRequest) return false
        return this.seller == other.seller
    }

    /** Returns the hash of the [GetAdSelectionDataRequest] object's data.  */
    override fun hashCode(): Int {
        return seller.hashCode()
    }

    /** Overrides the toString method.  */
    override fun toString(): String {
        return "GetAdSelectionDataRequest: seller=$seller"
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresExtension(extension = SdkExtensions.AD_SERVICES, version = 10)
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 10)
    internal fun convertToAdServices(): android.adservices.adselection.GetAdSelectionDataRequest {
        return android.adservices.adselection.GetAdSelectionDataRequest.Builder()
            .setSeller(seller?.convertToAdServices())
            .build()
    }
}
