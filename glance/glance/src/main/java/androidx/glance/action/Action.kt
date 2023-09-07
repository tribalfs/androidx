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

package androidx.glance.action

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceModifier

/**
 * An Action defines the actions a user can take. Implementations specify what operation will be
 * performed in response to the action, eg. [actionStartActivity] creates an Action that launches
 * the specified [Activity].
 */
interface Action

/**
 * Apply an [Action], to be executed in response to a user click.
 *
 * @param onClick The action to run.
 */
fun GlanceModifier.clickable(onClick: Action): GlanceModifier =
    this.then(ActionModifier(onClick))

/**
 * Run [block] in response to a user click.
 *
 * @param block The action to run.
 */
@Composable
fun GlanceModifier.clickable(
    block: () -> Unit
): GlanceModifier =
    this.then(ActionModifier(action(block = block)))

/**
 * Run [block] in response to a user click.
 *
 * @param rippleOverride A drawable resource to use as the onClick ripple. Use [NoRippleOverride]
 * if no custom behavior is needed.
 * @param block The action to run.v
 */
@Composable
fun GlanceModifier.clickable(
    @DrawableRes rippleOverride: Int = NoRippleOverride,
    block: () -> Unit
): GlanceModifier =
    this.then(ActionModifier(action = action(block = block), rippleOverride = rippleOverride))

/**
 * Apply an [Action], to be executed in response to a user click.
 *
 * @param rippleOverride A drawable resource to use as the onClick ripple. Use [NoRippleOverride]
 * if no custom behavior is needed.
 * @param onClick The action to run.
 */
fun GlanceModifier.clickable(
    onClick: Action,
    @DrawableRes rippleOverride: Int = NoRippleOverride
): GlanceModifier =
    this.then(ActionModifier(action = onClick, rippleOverride = rippleOverride))

/**
 * Run [block] in response to a user click.
 *
 * @param block The action to run.
 * @param rippleOverride A drawable resource to use as the onClick ripple. Use [NoRippleOverride]
 * if no custom behavior is needed.
 * @param key A stable and unique key that identifies the action for this element. This ensures
 * that the correct action is triggered, especially in cases of items that change order. If not
 * provided we use the key that is automatically generated by the Compose runtime, which is unique
 * for every exact code location in the composition tree.
 */
@ExperimentalGlanceApi
@Composable
fun GlanceModifier.clickable(
    key: String? = null,
    @DrawableRes rippleOverride: Int = NoRippleOverride,
    block: () -> Unit
): GlanceModifier =
    this.then(ActionModifier(action = action(key, block), rippleOverride = rippleOverride))

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ActionModifier(
    val action: Action,
    @DrawableRes val rippleOverride: Int = NoRippleOverride
) : GlanceModifier.Element {
    override fun toString(): String {
        return "ActionModifier(action=$action, rippleOverride=$rippleOverride)"
    }
}

/**
 * Constant. Tells the system that there is no ripple override. When this is passed, the system
 * will use default behavior for the ripple.
 */
@DrawableRes
const val NoRippleOverride = 0
