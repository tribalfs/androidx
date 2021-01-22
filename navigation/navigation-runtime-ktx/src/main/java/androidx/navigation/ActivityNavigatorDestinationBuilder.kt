/*
 * Copyright 2018 The Android Open Source Project
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

@file:Suppress("NOTHING_TO_INLINE")

package androidx.navigation

import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import androidx.annotation.IdRes
import kotlin.reflect.KClass

/**
 * Construct a new [ActivityNavigator.Destination]
 */
public inline fun NavGraphBuilder.activity(
    @IdRes id: Int,
    builder: ActivityNavigatorDestinationBuilder.() -> Unit
): Unit = destination(
    ActivityNavigatorDestinationBuilder(
        provider[ActivityNavigator::class],
        id
    ).apply(builder)
)

/**
 * DSL for constructing a new [ActivityNavigator.Destination]
 */
@NavDestinationDsl
public class ActivityNavigatorDestinationBuilder(
    navigator: ActivityNavigator,
    @IdRes id: Int
) : NavDestinationBuilder<ActivityNavigator.Destination>(navigator, id) {
    private val context = navigator.context

    public var targetPackage: String? = null

    public var activityClass: KClass<out Activity>? = null

    public var action: String? = null

    public var data: Uri? = null

    public var dataPattern: String? = null

    override fun build(): ActivityNavigator.Destination =
        super.build().also { destination ->
            destination.setTargetPackage(targetPackage)
            activityClass?.let { clazz ->
                destination.setComponentName(ComponentName(context, clazz.java))
            }
            destination.setAction(action)
            destination.setData(data)
            destination.setDataPattern(dataPattern)
        }
}
