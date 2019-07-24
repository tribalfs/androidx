/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.material.demos

import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.unaryPlus
import androidx.ui.core.Text
import androidx.ui.layout.Column
import androidx.ui.layout.MainAxisAlignment
import androidx.ui.material.samples.SimpleBottomAppBarCenterFab
import androidx.ui.material.samples.SimpleBottomAppBarEndFab
import androidx.ui.material.samples.SimpleBottomAppBarNoFab
import androidx.ui.material.samples.SimpleTopAppBar
import androidx.ui.material.samples.SimpleTopAppBarNavIcon
import androidx.ui.material.themeTextStyle
import androidx.ui.painting.imageFromResource

class AppBarActivity : MaterialDemoActivity() {

    @Composable
    override fun materialContent() {
        val favouriteImage = { imageFromResource(resources, R.drawable.ic_favorite) }
        val navigationImage = { imageFromResource(resources, R.drawable.ic_menu) }
        Column(mainAxisAlignment = MainAxisAlignment.SpaceBetween) {
            DemoText("TopAppBar")
            SimpleTopAppBar(favouriteImage)

            DemoText("TopAppBar - With navigation icon")
            SimpleTopAppBarNavIcon(favouriteImage, navigationImage)

            DemoText("BottomAppBar - No FAB")
            SimpleBottomAppBarNoFab(favouriteImage, navigationImage)

            DemoText("BottomAppBar - Center FAB")
            SimpleBottomAppBarCenterFab(favouriteImage, navigationImage)

            DemoText("BottomAppBar - End FAB")
            SimpleBottomAppBarEndFab(favouriteImage)
        }
    }

    @Composable
    private fun DemoText(text: String) {
        Text(text, style = +themeTextStyle { h6 })
    }
}
