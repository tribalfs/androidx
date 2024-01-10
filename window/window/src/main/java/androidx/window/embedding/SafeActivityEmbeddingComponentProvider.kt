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

package androidx.window.embedding

import android.app.Activity
import android.content.res.Configuration
import android.os.IBinder
import android.view.WindowMetrics
import androidx.annotation.VisibleForTesting
import androidx.window.SafeWindowExtensionsProvider
import androidx.window.core.ConsumerAdapter
import androidx.window.core.ExtensionsUtil
import androidx.window.extensions.WindowExtensions
import androidx.window.extensions.core.util.function.Consumer
import androidx.window.extensions.core.util.function.Function
import androidx.window.extensions.embedding.ActivityEmbeddingComponent
import androidx.window.extensions.embedding.ActivityStack
import androidx.window.extensions.embedding.AnimationBackground
import androidx.window.extensions.embedding.ParentContainerInfo
import androidx.window.extensions.embedding.SplitAttributes
import androidx.window.extensions.layout.WindowLayoutInfo
import androidx.window.reflection.ReflectionUtils.doesReturn
import androidx.window.reflection.ReflectionUtils.isPublic
import androidx.window.reflection.ReflectionUtils.validateReflection
import androidx.window.reflection.WindowExtensionsConstants.ACTIVITY_EMBEDDING_COMPONENT_CLASS
import java.util.concurrent.Executor

/**
 * Reflection Guard for [ActivityEmbeddingComponent].
 * This will go through the [ActivityEmbeddingComponent]'s method by reflection and
 * check each method's name and signature to see if the interface is what we required.
 */
internal class SafeActivityEmbeddingComponentProvider(
    private val loader: ClassLoader,
    private val consumerAdapter: ConsumerAdapter,
    private val windowExtensions: WindowExtensions
) {
    private val safeWindowExtensionsProvider = SafeWindowExtensionsProvider(loader)

    val activityEmbeddingComponent: ActivityEmbeddingComponent?
        get() {
            return if (canUseActivityEmbeddingComponent()) {
                try {
                    windowExtensions.activityEmbeddingComponent
                } catch (e: UnsupportedOperationException) {
                    null
                }
            } else {
                null
            }
        }

    private fun canUseActivityEmbeddingComponent(): Boolean {
        if (!isActivityEmbeddingComponentAccessible()) {
            return false
        }
        // TODO(b/267573854) : update logic to fallback to lower version
        //  if higher version is not matched
        return when (ExtensionsUtil.safeVendorApiLevel) {
            1 -> hasValidVendorApiLevel1()
            2 -> hasValidVendorApiLevel2()
            in 3..4 -> hasValidVendorApiLevel3() // No additional API in 4.
            in 5..Int.MAX_VALUE -> hasValidVendorApiLevel5()
            else -> false
        }
    }

    @VisibleForTesting
    internal fun isActivityEmbeddingComponentAccessible(): Boolean =
        safeWindowExtensionsProvider.isWindowExtensionsValid() &&
            isActivityEmbeddingComponentValid()

    /**
     * Vendor API level 1 includes the following methods:
     *  - [ActivityEmbeddingComponent.setEmbeddingRules]
     *  - [ActivityEmbeddingComponent.isActivityEmbedded]
     *  - [ActivityEmbeddingComponent.setSplitInfoCallback] with [java.util.function.Consumer]
     * and following classes: TODO(b/268583307) : add guard function for those classes
     *  - [androidx.window.extensions.embedding.ActivityRule]
     *  - [androidx.window.extensions.embedding.SplitPairRule]
     *  - [androidx.window.extensions.embedding.SplitPlaceholderRule]
     *  - [androidx.window.extensions.embedding.SplitInfo]
     */
    @VisibleForTesting
    internal fun hasValidVendorApiLevel1(): Boolean {
        return isMethodSetEmbeddingRulesValid() &&
            isMethodIsActivityEmbeddedValid() &&
            isMethodSetSplitInfoCallbackJavaConsumerValid()
    }

    /**
     * Vendor API level 2 includes the following methods
     *  - [ActivityEmbeddingComponent.setSplitInfoCallback] with [Consumer]
     *  - [ActivityEmbeddingComponent.clearSplitInfoCallback]
     *  - [ActivityEmbeddingComponent.setSplitAttributesCalculator]
     *  - [ActivityEmbeddingComponent.clearSplitAttributesCalculator]
     * and following classes: TODO(b/268583307) : add guard function for those classes
     *  - [androidx.window.extensions.embedding.SplitAttributes]
     */
    @VisibleForTesting
    internal fun hasValidVendorApiLevel2(): Boolean {
        return hasValidVendorApiLevel1() &&
            isMethodSetSplitInfoCallbackWindowConsumerValid() &&
            isMethodClearSplitInfoCallbackValid() &&
            isMethodSplitAttributesCalculatorValid()
    }

    /**
     * Vendor API level 3 includes the following methods:
     * - [ActivityEmbeddingComponent.updateSplitAttributes]
     * - [ActivityEmbeddingComponent.invalidateTopVisibleSplitAttributes]
     */
    @VisibleForTesting
    internal fun hasValidVendorApiLevel3(): Boolean =
        hasValidVendorApiLevel2() &&
            isMethodInvalidateTopVisibleSplitAttributesValid() &&
            isMethodUpdateSplitAttributesValid()

    /**
     * Vendor API level 5 includes the following methods:
     * - [ActivityEmbeddingComponent.clearActivityStackAttributesCalculator]
     * - [ActivityEmbeddingComponent.getActivityStackToken]
     * - [ActivityEmbeddingComponent.getParentContainerInfo]
     * - [ActivityEmbeddingComponent.setActivityStackAttributesCalculator]
     * - [ActivityEmbeddingComponent.registerActivityStackCallback]
     * - [ActivityEmbeddingComponent.unregisterActivityStackCallback]
     * - [ActivityStack.getTag]
     * - [ActivityStack.getToken]
     * - [AnimationBackground.createColorBackground]
     * - [AnimationBackground.ANIMATION_BACKGROUND_DEFAULT]
     * - [ParentContainerInfo]
     * // TODO(b/316493273): Guard other AEComponentMethods
     */
    @VisibleForTesting
    internal fun hasValidVendorApiLevel5(): Boolean =
        hasValidVendorApiLevel3() &&
            isClassAnimationBackgroundValid() &&
            isClassParentContainerInfoValid() &&
            isActivityStackGetTagValid() &&
            isActivityStackGetTokenValid() &&
            isMethodGetActivityStackTokenValid() &&
            isMethodGetParentContainerInfoValid() &&
            isMethodSetActivityStackAttributesCalculatorValid() &&
            isMethodClearActivityStackAttributesCalculatorValid() &&
            isMethodRegisterActivityStackCallbackValid() &&
            isMethodUnregisterActivityStackCallbackValid()

    private fun isMethodSetEmbeddingRulesValid(): Boolean {
        return validateReflection("ActivityEmbeddingComponent#setEmbeddingRules is not valid") {
            val setEmbeddingRulesMethod = activityEmbeddingComponentClass.getMethod(
                "setEmbeddingRules",
                Set::class.java
            )
            setEmbeddingRulesMethod.isPublic
        }
    }

    private fun isMethodIsActivityEmbeddedValid(): Boolean {
        return validateReflection("ActivityEmbeddingComponent#isActivityEmbedded is not valid") {
            val isActivityEmbeddedMethod = activityEmbeddingComponentClass.getMethod(
                "isActivityEmbedded",
                Activity::class.java
            )
            isActivityEmbeddedMethod.isPublic &&
                isActivityEmbeddedMethod.doesReturn(Boolean::class.java)
        }
    }

    private fun isMethodClearSplitInfoCallbackValid(): Boolean {
        return validateReflection(
            "ActivityEmbeddingComponent#clearSplitInfoCallback is not valid"
        ) {
            val clearSplitInfoCallbackMethod =
                activityEmbeddingComponentClass.getMethod("clearSplitInfoCallback")
            clearSplitInfoCallbackMethod.isPublic
        }
    }

    private fun isMethodSplitAttributesCalculatorValid(): Boolean {
        return validateReflection(
            "ActivityEmbeddingComponent#setSplitAttributesCalculator is not valid"
        ) {
            val setSplitAttributesCalculatorMethod = activityEmbeddingComponentClass.getMethod(
                "setSplitAttributesCalculator",
                Function::class.java
            )
            val clearSplitAttributesCalculatorMethod =
                activityEmbeddingComponentClass.getMethod("clearSplitAttributesCalculator")
            setSplitAttributesCalculatorMethod.isPublic &&
                clearSplitAttributesCalculatorMethod.isPublic
        }
    }

    private fun isMethodSetSplitInfoCallbackJavaConsumerValid(): Boolean {
        return validateReflection("ActivityEmbeddingComponent#setSplitInfoCallback is not valid") {
            val consumerClass =
                consumerAdapter.consumerClassOrNull() ?: return@validateReflection false
            val setSplitInfoCallbackMethod =
                activityEmbeddingComponentClass.getMethod("setSplitInfoCallback", consumerClass)
            setSplitInfoCallbackMethod.isPublic
        }
    }

    private fun isMethodSetSplitInfoCallbackWindowConsumerValid(): Boolean {
        return validateReflection("ActivityEmbeddingComponent#setSplitInfoCallback is not valid") {
            val setSplitInfoCallbackMethod = activityEmbeddingComponentClass.getMethod(
                "setSplitInfoCallback",
                Consumer::class.java
            )
            setSplitInfoCallbackMethod.isPublic
        }
    }

    private fun isMethodInvalidateTopVisibleSplitAttributesValid(): Boolean =
        validateReflection("#invalidateTopVisibleSplitAttributes is not valid") {
            val invalidateTopVisibleSplitAttributesMethod = activityEmbeddingComponentClass
                .getMethod(
                    "invalidateTopVisibleSplitAttributes"
                )
            invalidateTopVisibleSplitAttributesMethod.isPublic
        }

    private fun isMethodUpdateSplitAttributesValid(): Boolean =
        validateReflection("#updateSplitAttributes is not valid") {
            val updateSplitAttributesMethod = activityEmbeddingComponentClass.getMethod(
                "updateSplitAttributes",
                IBinder::class.java,
                SplitAttributes::class.java
            )
            updateSplitAttributesMethod.isPublic
        }

    private fun isClassAnimationBackgroundValid(): Boolean =
        validateReflection("Class AnimationBackground is not valid") {
            val animationBackgroundClass = AnimationBackground::class.java
            val colorBackgroudClass = AnimationBackground.ColorBackground::class.java
            val createColorBackgroundMethod = animationBackgroundClass.getMethod(
                "createColorBackground",
                Int::class.javaPrimitiveType
            )
            val animationBackgroundDefaultField = animationBackgroundClass.getDeclaredField(
                "ANIMATION_BACKGROUND_DEFAULT"
            )
            val colorBackgroundGetColor = colorBackgroudClass.getMethod(
                "getColor"
            )
            createColorBackgroundMethod.isPublic &&
                createColorBackgroundMethod.doesReturn(colorBackgroudClass) &&
                animationBackgroundDefaultField.isPublic &&
                colorBackgroundGetColor.isPublic &&
                colorBackgroundGetColor.doesReturn(Int::class.javaPrimitiveType!!)
        }

    private fun isActivityStackGetTokenValid(): Boolean =
        validateReflection("ActivityStack#getToken is not valid") {
            val activityStackClass = ActivityStack::class.java
            val getTokenMethod = activityStackClass.getMethod("getToken")

            getTokenMethod.isPublic && getTokenMethod.doesReturn(IBinder::class.java)
        }

    private fun isActivityStackGetTagValid(): Boolean =
        validateReflection("ActivityStack#getTag is not valid") {
            val activityStackClass = ActivityStack::class.java
            val getTokenMethod = activityStackClass.getMethod("getTag")

            getTokenMethod.isPublic && getTokenMethod.doesReturn(String::class.java)
        }

    @Suppress("newApi") // Suppress lint check for WindowMetrics
    private fun isClassParentContainerInfoValid(): Boolean =
        validateReflection("ParentContainerInfo is not valid") {
            val parentContainerInfoClass = ParentContainerInfo::class.java
            val getWindowMetricsMethod = parentContainerInfoClass.getMethod("getWindowMetrics")
            val getConfigurationMethod = parentContainerInfoClass.getMethod("getConfiguration")
            val getWindowLayoutInfoMethod = parentContainerInfoClass
                .getMethod("getWindowLayoutInfo")

            getWindowMetricsMethod.isPublic &&
                getWindowMetricsMethod.doesReturn(WindowMetrics::class.java) &&
                getConfigurationMethod.isPublic &&
                getConfigurationMethod.doesReturn(Configuration::class.java) &&
                getWindowLayoutInfoMethod.isPublic &&
                getWindowLayoutInfoMethod.doesReturn(WindowLayoutInfo::class.java)
        }

    private fun isMethodGetParentContainerInfoValid(): Boolean =
        validateReflection {
            val getParentContainerInfoMethod = activityEmbeddingComponentClass.getMethod(
                "getParentContainerInfo",
                IBinder::class.java
            )
            getParentContainerInfoMethod.isPublic &&
                getParentContainerInfoMethod.doesReturn(ParentContainerInfo::class.java)
        }

    private fun isMethodGetActivityStackTokenValid(): Boolean =
        validateReflection("getActivityStackToken is not valid") {
            val getActivityStackTokenMethod = activityEmbeddingComponentClass.getMethod(
                "getActivityStackToken",
                String::class.java
            )
            getActivityStackTokenMethod.isPublic &&
                getActivityStackTokenMethod.doesReturn(IBinder::class.java)
        }

    private fun isMethodSetActivityStackAttributesCalculatorValid(): Boolean =
        validateReflection("setActivityStackAttributesCalculator is not valid") {
            val setActivityStackAttributesCalculatorMethod = activityEmbeddingComponentClass
                .getMethod("setActivityStackAttributesCalculator", Function::class.java)
            setActivityStackAttributesCalculatorMethod.isPublic
        }

    private fun isMethodClearActivityStackAttributesCalculatorValid(): Boolean =
        validateReflection("clearActivityStackAttributesCalculator is not valid") {
            val setActivityStackAttributesCalculatorMethod = activityEmbeddingComponentClass
                .getMethod("clearActivityStackAttributesCalculator")
            setActivityStackAttributesCalculatorMethod.isPublic
        }

    private fun isMethodRegisterActivityStackCallbackValid(): Boolean =
        validateReflection("registerActivityStackCallback is not valid") {
            val registerActivityStackCallbackMethod = activityEmbeddingComponentClass
                .getMethod(
                    "registerActivityStackCallback",
                    Executor::class.java,
                    Consumer::class.java
                )
            registerActivityStackCallbackMethod.isPublic
        }

    private fun isMethodUnregisterActivityStackCallbackValid(): Boolean =
        validateReflection("unregisterActivityStackCallback is not valid") {
            val unregisterActivityStackCallbackMethod = activityEmbeddingComponentClass
                .getMethod(
                    "unregisterActivityStackCallback",
                    Consumer::class.java
                )
            unregisterActivityStackCallbackMethod.isPublic
        }

    private fun isActivityEmbeddingComponentValid(): Boolean {
        return validateReflection("WindowExtensions#getActivityEmbeddingComponent is not valid") {
            val extensionsClass = safeWindowExtensionsProvider.windowExtensionsClass
            val getActivityEmbeddingComponentMethod =
                extensionsClass.getMethod("getActivityEmbeddingComponent")
            val activityEmbeddingComponentClass = activityEmbeddingComponentClass
            getActivityEmbeddingComponentMethod.isPublic &&
                getActivityEmbeddingComponentMethod.doesReturn(activityEmbeddingComponentClass)
        }
    }

    private val activityEmbeddingComponentClass: Class<*>
        get() {
            return loader.loadClass(ACTIVITY_EMBEDDING_COMPONENT_CLASS)
        }
}
