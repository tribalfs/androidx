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

package androidx.privacysandbox.ads.adservices.customaudience

import android.adservices.common.AdServicesPermissions
import android.adservices.customaudience.CustomAudienceManager
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresPermission
import androidx.core.os.BuildCompat
import androidx.core.os.asOutcomeReceiver
import androidx.privacysandbox.ads.adservices.common.AdData
import androidx.privacysandbox.ads.adservices.common.AdSelectionSignals
import androidx.privacysandbox.ads.adservices.common.AdTechIdentifier
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * This class provides APIs for app and ad-SDKs to join / leave custom audiences.
 */
abstract class CustomAudienceManager internal constructor() {
    /**
     * Adds the user to the given {@link CustomAudience}.
     *
     * <p>An attempt to register the user for a custom audience with the same combination of {@code
     * ownerPackageName}, {@code buyer}, and {@code name} will cause the existing custom audience's
     * information to be overwritten, including the list of ads data.
     *
     * <p>Note that the ads list can be completely overwritten by the daily background fetch job.
     *
     * <p>This call fails with an {@link SecurityException} if
     *
     * <ol>
     *   <li>the {@code ownerPackageName} is not calling app's package name and/or
     *   <li>the buyer is not authorized to use the API.
     * </ol>
     *
     * <p>This call fails with an {@link IllegalArgumentException} if
     *
     * <ol>
     *   <li>the storage limit has been exceeded by the calling application and/or
     *   <li>any URI parameters in the {@link CustomAudience} given are not authenticated with the
     *       {@link CustomAudience} buyer.
     * </ol>
     *
     * <p>This call fails with {@link LimitExceededException} if the calling package exceeds the
     * allowed rate limits and is throttled.
     *
     * <p>This call fails with an {@link IllegalStateException} if an internal service error is
     * encountered.
     *
     * @param request The request to join custom audience.
     */
    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    abstract suspend fun joinCustomAudience(request: JoinCustomAudienceRequest)

    /**
     * Attempts to remove a user from a custom audience by deleting any existing {@link
     * CustomAudience} data, identified by {@code ownerPackageName}, {@code buyer}, and {@code
     * name}.
     *
     * <p>This call fails with an {@link SecurityException} if
     *
     * <ol>
     *   <li>the {@code ownerPackageName} is not calling app's package name; and/or
     *   <li>the buyer is not authorized to use the API.
     * </ol>
     *
     * <p>This call fails with {@link LimitExceededException} if the calling package exceeds the
     * allowed rate limits and is throttled.
     *
     * <p>This call does not inform the caller whether the custom audience specified existed in
     * on-device storage. In other words, it will fail silently when a buyer attempts to leave a
     * custom audience that was not joined.
     *
     * @param request The request to leave custom audience.
     */
    @DoNotInline
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
    abstract suspend fun leaveCustomAudience(request: LeaveCustomAudienceRequest)

    @SuppressLint("ClassVerificationFailure", "NewApi")
    private class Api33Ext4Impl(
        private val customAudienceManager: CustomAudienceManager
        ) : androidx.privacysandbox.ads.adservices.customaudience.CustomAudienceManager() {
        constructor(context: Context) : this(
            context.getSystemService<CustomAudienceManager>(
                CustomAudienceManager::class.java
            )
        )

        @DoNotInline
        @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
        override suspend fun joinCustomAudience(request: JoinCustomAudienceRequest) {
            suspendCancellableCoroutine { continuation ->
                customAudienceManager.joinCustomAudience(
                    convertJoinRequest(request),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }

        @DoNotInline
        @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE)
        override suspend fun leaveCustomAudience(request: LeaveCustomAudienceRequest) {
            suspendCancellableCoroutine { continuation ->
                customAudienceManager.leaveCustomAudience(
                    convertLeaveRequest(request),
                    Runnable::run,
                    continuation.asOutcomeReceiver()
                )
            }
        }

        private fun convertJoinRequest(
            request: JoinCustomAudienceRequest
        ): android.adservices.customaudience.JoinCustomAudienceRequest {
            return android.adservices.customaudience.JoinCustomAudienceRequest.Builder()
                .setCustomAudience(convertCustomAudience(request.customAudience))
                .build()
        }

        private fun convertLeaveRequest(
            request: LeaveCustomAudienceRequest
        ): android.adservices.customaudience.LeaveCustomAudienceRequest {
            return android.adservices.customaudience.LeaveCustomAudienceRequest.Builder()
                .setBuyer(convertAdTechIdentifier(request.buyer))
                .setName(request.name)
                .build()
        }

        private fun convertCustomAudience(
            request: CustomAudience
        ): android.adservices.customaudience.CustomAudience {
            return android.adservices.customaudience.CustomAudience.Builder()
                .setActivationTime(request.activationTime)
                .setAds(convertAdData(request.ads))
                .setBiddingLogicUri(request.biddingLogicUri)
                .setBuyer(convertAdTechIdentifier(request.buyer))
                .setDailyUpdateUri(request.dailyUpdateUri)
                .setExpirationTime(request.expirationTime)
                .setName(request.name)
                .setTrustedBiddingData(convertTrustedSignals(request.trustedBiddingSignals))
                .setUserBiddingSignals(convertBiddingSignals(request.userBiddingSignals))
                .build()
        }

        private fun convertAdData(
            input: List<AdData>
        ): List<android.adservices.common.AdData> {
            val result = mutableListOf<android.adservices.common.AdData>()
            for (ad in input) {
                result.add(android.adservices.common.AdData.Builder()
                    .setMetadata(ad.metadata)
                    .setRenderUri(ad.renderUri)
                    .build())
            }
            return result
        }

        private fun convertAdTechIdentifier(
            input: AdTechIdentifier
        ): android.adservices.common.AdTechIdentifier {
            return android.adservices.common.AdTechIdentifier.fromString(input.identifier)
        }

        private fun convertTrustedSignals(
            input: TrustedBiddingData?
        ): android.adservices.customaudience.TrustedBiddingData? {
            if (input == null) return null
            return android.adservices.customaudience.TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(input.trustedBiddingKeys)
                .setTrustedBiddingUri(input.trustedBiddingUri)
                .build()
        }

        private fun convertBiddingSignals(
            input: AdSelectionSignals?
        ): android.adservices.common.AdSelectionSignals? {
            if (input == null) return null
            return android.adservices.common.AdSelectionSignals.fromString(input.signals)
        }
    }

    companion object {
        /**
         *  Creates [CustomAudienceManager].
         *
         *  @return CustomAudienceManager object.
         */
        @JvmStatic
        @androidx.annotation.OptIn(markerClass = [BuildCompat.PrereleaseSdkCheck::class])
        @SuppressLint("NewApi", "ClassVerificationFailure")
        fun obtain(context: Context):
            androidx.privacysandbox.ads.adservices.customaudience.CustomAudienceManager? {
            // TODO: Add check SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES) >= 4
            return if (BuildCompat.isAtLeastU()) {
                Api33Ext4Impl(context)
            } else {
                null
            }
        }
    }
}
