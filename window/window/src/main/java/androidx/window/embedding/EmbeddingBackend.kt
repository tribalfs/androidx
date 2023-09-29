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
import android.content.Context
import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import androidx.window.RequiresWindowSdkExtension
import java.util.concurrent.Executor

/**
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface EmbeddingBackend {
    fun setRules(rules: Set<EmbeddingRule>)

    fun getRules(): Set<EmbeddingRule>

    fun addRule(rule: EmbeddingRule)

    fun removeRule(rule: EmbeddingRule)

    fun addSplitListenerForActivity(
        activity: Activity,
        executor: Executor,
        callback: Consumer<List<SplitInfo>>
    )

    fun removeSplitListenerForActivity(
        consumer: Consumer<List<SplitInfo>>
    )

    val splitSupportStatus: SplitController.SplitSupportStatus

    fun isActivityEmbedded(activity: Activity): Boolean

    @RequiresWindowSdkExtension(2)
    fun setSplitAttributesCalculator(
        calculator: (SplitAttributesCalculatorParams) -> SplitAttributes
    )

    @RequiresWindowSdkExtension(2)
    fun clearSplitAttributesCalculator()

    fun getActivityStack(activity: Activity): ActivityStack?

    @RequiresWindowSdkExtension(3)
    fun setLaunchingActivityStack(options: ActivityOptions, token: IBinder): ActivityOptions

    @RequiresWindowSdkExtension(3)
    fun invalidateTopVisibleSplitAttributes()

    @RequiresWindowSdkExtension(3)
    fun updateSplitAttributes(splitInfo: SplitInfo, splitAttributes: SplitAttributes)

    companion object {

        private var decorator: (EmbeddingBackend) -> EmbeddingBackend =
            { it }

        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        fun getInstance(context: Context): EmbeddingBackend {
            return decorator(ExtensionEmbeddingBackend.getInstance(context))
        }

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun overrideDecorator(overridingDecorator: EmbeddingBackendDecorator) {
            decorator = overridingDecorator::decorate
        }

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun reset() {
            decorator = { it }
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface EmbeddingBackendDecorator {

    /**
     * Returns an instance of [EmbeddingBackend]
     */
    fun decorate(embeddingBackend: EmbeddingBackend): EmbeddingBackend
}
