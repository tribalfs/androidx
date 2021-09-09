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
import androidx.glance.GlanceInternalApi

@GlanceInternalApi
class LaunchActivityAction(val activityClass: Class<out Activity>) : Action

/**
 * Creates an [Action] that launches the specified [Activity] when triggered.
 */
@OptIn(GlanceInternalApi::class)
public fun <T : Activity> launchActivityAction(activity: Class<T>): Action =
    LaunchActivityAction(activity)

@Suppress("MissingNullability") /* Shouldn't need to specify @NonNull. b/199284086 */
@OptIn(GlanceInternalApi::class)
/**
 * Creates an [Action] that launches the specified [Activity] when triggered.
 */
public inline fun <reified T : Activity> launchActivityAction(): Action =
    launchActivityAction(T::class.java)