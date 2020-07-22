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

package androidx.ui.material

import android.os.Build
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.core.Modifier
import androidx.ui.core.testTag
import androidx.compose.foundation.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.ui.test.ComposeTestRule
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class DrawerScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    private fun ComposeTestRule.setBottomDrawer(drawerState: BottomDrawerState) {
        setMaterialContent {
            Box(Modifier.size(10.dp, 100.dp).testTag("container")) {
                BottomDrawerLayout(
                    drawerState = drawerState,
                    onStateChange = {},
                    drawerContent = { Box(Modifier.fillMaxSize(), backgroundColor = Color.Red) },
                    bodyContent = { Box(Modifier.fillMaxSize(), backgroundColor = Color.Yellow) }
                )
            }
        }
    }

    private fun ComposeTestRule.setModalDrawer(drawerState: DrawerState) {
        setMaterialContent {
            Box(Modifier.size(100.dp, 10.dp).testTag("container")) {
                ModalDrawerLayout(
                    drawerState = drawerState,
                    onStateChange = {},
                    drawerContent = { Box(Modifier.fillMaxSize(), backgroundColor = Color.Red) },
                    bodyContent = { Box(Modifier.fillMaxSize(), backgroundColor = Color.Yellow) }
                )
            }
        }
    }

    @Test
    fun bottomDrawer_closed() {
        composeTestRule.setBottomDrawer(BottomDrawerState.Closed)
        assertScreenshotAgainstGolden("bottomDrawer_closed")
    }

    @Test
    fun modalDrawer_closed() {
        composeTestRule.setModalDrawer(DrawerState.Closed)
        assertScreenshotAgainstGolden("modalDrawer_closed")
    }

    @Test
    fun bottomDrawer_opened() {
        composeTestRule.setBottomDrawer(BottomDrawerState.Opened)
        assertScreenshotAgainstGolden("bottomDrawer_opened")
    }

    @Test
    fun modalDrawer_opened() {
        composeTestRule.setModalDrawer(DrawerState.Opened)
        assertScreenshotAgainstGolden("modalDrawer_opened")
    }

    private fun assertScreenshotAgainstGolden(goldenName: String) {
        onNodeWithTag("container")
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenName)
    }
}
