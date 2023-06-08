/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.window.embedding

import android.app.Activity
import android.app.ActivityOptions
import android.os.IBinder
import androidx.window.extensions.embedding.ActivityEmbeddingComponent

/**
 * Adapter interface for different historical versions of activity embedding OEM interface in
 * [ActivityEmbeddingComponent].
 */
internal interface EmbeddingInterfaceCompat {

    fun setRules(rules: Set<EmbeddingRule>)

    fun setEmbeddingCallback(embeddingCallback: EmbeddingCallbackInterface)

    interface EmbeddingCallbackInterface {
        fun onSplitInfoChanged(splitInfo: List<SplitInfo>)
    }

    fun isActivityEmbedded(activity: Activity): Boolean

    fun setSplitAttributesCalculator(
        calculator: (SplitAttributesCalculatorParams) -> SplitAttributes
    )

    fun clearSplitAttributesCalculator()

    fun isSplitAttributesCalculatorSupported(): Boolean

    fun setLaunchingActivityStack(options: ActivityOptions, token: IBinder): ActivityOptions

    fun finishActivityStacks(activityStacks: Set<ActivityStack>)

    fun isFinishActivityStacksSupported(): Boolean

    fun invalidateTopVisibleSplitAttributes()

    fun updateSplitAttributes(splitInfo: SplitInfo, splitAttributes: SplitAttributes)

    fun areSplitAttributesUpdatesSupported(): Boolean
}