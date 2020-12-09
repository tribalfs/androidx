/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.get
import androidx.navigation.navigation

/**
 * Add the [Composable] to the [NavGraphBuilder]
 *
 * @param route route for the destination
 * @param arguments list of arguments to associate with destination
 * @param deepLinks list of deep links to associate with the destinations
 * @param content composable for the destination
 */
public fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    addDestination(
        ComposeNavigator.Destination(provider[ComposeNavigator::class], content).apply {
            val internalRoute = createRoute(route)
            addDeepLink(internalRoute)
            val argument = navArgument(KEY_ROUTE) { defaultValue = route }
            addArgument(argument.component1(), argument.component2())
            id = internalRoute.hashCode()
            arguments.forEach { (argumentName, argument) ->
                addArgument(argumentName, argument)
            }
            deepLinks.forEach { deepLink ->
                addDeepLink(deepLink)
            }
        }
    )
}

/**
 * Construct a nested [NavGraph]
 *
 * @sample androidx.navigation.compose.samples.NestedNavStartDestination
 * @sample androidx.navigation.compose.samples.NestedNavInGraph
 */
public fun NavGraphBuilder.navigation(
    startDestination: String,
    route: String,
    builder: NavGraphBuilder.() -> Unit
): Unit = navigation(
    createRoute(route).hashCode(),
    createRoute(startDestination).hashCode()
) {
    deepLink(createRoute(route))
    apply(builder)
}
