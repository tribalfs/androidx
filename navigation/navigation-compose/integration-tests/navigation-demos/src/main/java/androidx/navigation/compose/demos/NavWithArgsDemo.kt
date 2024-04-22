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

package androidx.navigation.compose.demos

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.samples.Dashboard
import androidx.navigation.compose.samples.Profile
import androidx.navigation.compose.samples.ProfileWithArgs
import androidx.navigation.toRoute

@Composable
fun NavWithArgsDemo() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Profile::class) {
        composable<Profile> { ProfileWithArgs(navController) }
        composable<Dashboard> { backStackEntry ->
            val dashboard = backStackEntry.toRoute<Dashboard>()
            Dashboard(navController, dashboard.userId)
        }
    }
}
