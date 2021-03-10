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

package androidx.compose.material

import android.os.SystemClock.sleep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.GestureScope
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterialApi::class)
class DrawerTest {

    @get:Rule
    val rule = createComposeRule()

    private val bottomDrawerTag = "drawerContentTag"
    private val shortBottomDrawerHeight = 256.dp

    private fun advanceClock() {
        rule.mainClock.advanceTimeBy(100_000L)
    }

    @Test
    fun modalDrawer_testOffset_whenOpen() {
        rule.setMaterialContent {
            val drawerState = rememberDrawerState(DrawerValue.Open)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("content"))
                },
                content = {}
            )
        }

        rule.onNodeWithTag("content")
            .assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun modalDrawer_testOffset_whenClosed() {
        rule.setMaterialContent {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("content"))
                },
                content = {}
            )
        }

        val width = rule.rootWidth()
        rule.onNodeWithTag("content")
            .assertLeftPositionInRootIsEqualTo(-width)
    }

    @Test
    fun modalDrawer_testEndPadding_whenOpen() {
        rule.setMaterialContent {
            val drawerState = rememberDrawerState(DrawerValue.Open)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("content"))
                },
                content = {}
            )
        }

        rule.onNodeWithTag("content")
            .assertWidthIsEqualTo(rule.rootWidth() - 56.dp)
    }

    @Test
    fun bottomDrawer_testOffset_shortDrawer_whenClosed() {
        rule.setMaterialContent {
            val drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val height = rule.rootHeight()
        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(height)
    }

    @Test
    fun bottomDrawer_testOffset_shortDrawer_whenExpanded() {
        rule.setMaterialContent {
            val drawerState = rememberBottomDrawerState(BottomDrawerValue.Expanded)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.height(shortBottomDrawerHeight).testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val height = rule.rootHeight()
        val expectedTop = height - shortBottomDrawerHeight
        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(expectedTop)
    }

    @Test
    fun bottomDrawer_testOffset_tallDrawer_whenClosed() {
        rule.setMaterialContent {
            val drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val height = rule.rootHeight()
        val expectedTop = height
        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(expectedTop)
    }

    @Test
    @Ignore // Disabled until b/178529942 is fixed
    fun bottomDrawer_testOffset_tallDrawer_whenOpen() {
        rule.setMaterialContent {
            val drawerState = rememberBottomDrawerState(BottomDrawerValue.Open)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val width = rule.rootWidth()
        val height = rule.rootHeight()
        val expectedTop = if (width > height) 0.dp else (height / 2)
        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(expectedTop)
    }

    @Test
    fun bottomDrawer_testOffset_tallDrawer_whenExpanded() {
        rule.setMaterialContent {
            val drawerState = rememberBottomDrawerState(BottomDrawerValue.Expanded)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val expectedTop = 0.dp
        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(expectedTop)
    }

    @Test
    @SmallTest
    fun bottomDrawer_hasPaneTitle() {
        rule.setMaterialContent {
            BottomDrawer(
                drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed),
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        rule.onNodeWithTag(bottomDrawerTag, useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
    }

    @Test
    @LargeTest
    fun modalDrawer_openAndClose(): Unit = runBlocking {
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("drawer"))
                },
                content = {}
            )
        }

        val width = rule.rootWidth()

        // Drawer should start in closed state
        rule.onNodeWithTag("drawer").assertLeftPositionInRootIsEqualTo(-width)

        // When the drawer state is set to Opened
        drawerState.open()
        // Then the drawer should be opened
        rule.onNodeWithTag("drawer").assertLeftPositionInRootIsEqualTo(0.dp)

        // When the drawer state is set to Closed
        drawerState.close()
        // Then the drawer should be closed
        rule.onNodeWithTag("drawer").assertLeftPositionInRootIsEqualTo(-width)
    }

    @Test
    @LargeTest
    fun modalDrawer_bodyContent_clickable(): Unit = runBlocking {
        var drawerClicks = 0
        var bodyClicks = 0
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            // emulate click on the screen
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().clickable { drawerClicks += 1 })
                },
                content = {
                    Box(Modifier.testTag("Drawer").fillMaxSize().clickable { bodyClicks += 1 })
                }
            )
        }

        // Click in the middle of the drawer (which is the middle of the body)
        rule.onNodeWithTag("Drawer").performGesture { click() }

        rule.runOnIdle {
            assertThat(drawerClicks).isEqualTo(0)
            assertThat(bodyClicks).isEqualTo(1)
        }
        drawerState.open()

        // Click on the left-center pixel of the drawer
        rule.onNodeWithTag("Drawer").performGesture {
            click(centerLeft)
        }

        rule.runOnIdle {
            assertThat(drawerClicks).isEqualTo(1)
            assertThat(bodyClicks).isEqualTo(1)
        }
    }

    @Test
    @LargeTest
    fun modalDrawer_drawerContent_doesntPropagateClicksWhenOpen(): Unit = runBlocking {
        var bodyClicks = 0
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("Drawer"))
                },
                content = {
                    Box(Modifier.fillMaxSize().clickable { bodyClicks += 1 })
                }
            )
        }

        // Click in the middle of the drawer
        rule.onNodeWithTag("Drawer").performClick()

        rule.runOnIdle {
            assertThat(bodyClicks).isEqualTo(1)
        }
        drawerState.open()

        // Click on the left-center pixel of the drawer
        rule.onNodeWithTag("Drawer").performGesture {
            click(centerLeft)
        }

        rule.runOnIdle {
            assertThat(bodyClicks).isEqualTo(1)
        }
    }

    @Test
    @LargeTest
    fun modalDrawer_openBySwipe() {
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            Box(Modifier.testTag("Drawer")) {
                ModalDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        Box(Modifier.fillMaxSize().background(color = Color.Magenta))
                    },
                    content = {
                        Box(Modifier.fillMaxSize().background(color = Color.Red))
                    }
                )
            }
        }

        rule.onNodeWithTag("Drawer")
            .performGesture { swipeRight() }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(DrawerValue.Open)
        }

        rule.onNodeWithTag("Drawer")
            .performGesture { swipeLeft() }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(DrawerValue.Closed)
        }
    }

    @Test
    @LargeTest
    fun modalDrawer_openBySwipe_rtl() {
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            // emulate click on the screen
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.testTag("Drawer")) {
                    ModalDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            Box(Modifier.fillMaxSize().background(color = Color.Magenta))
                        },
                        content = {
                            Box(Modifier.fillMaxSize().background(color = Color.Red))
                        }
                    )
                }
            }
        }

        rule.onNodeWithTag("Drawer")
            .performGesture { swipeLeft() }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(DrawerValue.Open)
        }

        rule.onNodeWithTag("Drawer")
            .performGesture { swipeRight() }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(DrawerValue.Closed)
        }
    }

    @Test
    @LargeTest
    fun modalDrawer_noDismissActionWhenClosed_hasDissmissActionWhenOpen(): Unit = runBlocking {
        lateinit var drawerState: DrawerState
        rule.setMaterialContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag("drawer"))
                },
                content = {}
            )
        }

        // Drawer should start in closed state and have no dismiss action
        rule.onNodeWithTag("drawer", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Dismiss))

        // When the drawer state is set to Opened
        drawerState.open()
        // Then the drawer should be opened and have dismiss action
        rule.onNodeWithTag("drawer", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))

        // When the drawer state is set to Closed using dismiss action
        rule.onNodeWithTag("drawer", useUnmergedTree = true)
            .onParent()
            .performSemanticsAction(SemanticsActions.Dismiss)
        // Then the drawer should be closed and have no dismiss action
        rule.onNodeWithTag("drawer", useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Dismiss))
    }

    @Test
    fun bottomDrawer_bodyContent_clickable(): Unit = runBlocking {
        var drawerClicks = 0
        var bodyClicks = 0
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            // emulate click on the screen
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().clickable { drawerClicks += 1 })
                },
                content = {
                    Box(
                        Modifier
                            .testTag(bottomDrawerTag)
                            .fillMaxSize()
                            .clickable { bodyClicks += 1 }
                    )
                }
            )
        }

        // Click in the middle of the drawer (which is the middle of the body)
        rule.onNodeWithTag(bottomDrawerTag).performGesture { click() }

        rule.runOnIdle {
            assertThat(drawerClicks).isEqualTo(0)
            assertThat(bodyClicks).isEqualTo(1)
        }

        drawerState.open()
        sleep(100) // TODO(147586311): remove this sleep when opening the drawer triggers a wait

        // Click on the bottom-center pixel of the drawer
        rule.onNodeWithTag(bottomDrawerTag).performGesture {
            click(bottomCenter)
        }

        assertThat(drawerClicks).isEqualTo(1)
        assertThat(bodyClicks).isEqualTo(1)
    }

    @Test
    @LargeTest
    fun bottomDrawer_drawerContent_doesntPropagateClicksWhenOpen(): Unit = runBlocking {
        var bodyClicks = 0
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {
                    Box(Modifier.fillMaxSize().clickable { bodyClicks += 1 })
                }
            )
        }

        // Click in the middle of the drawer
        rule.onNodeWithTag(bottomDrawerTag).performClick()

        rule.runOnIdle {
            assertThat(bodyClicks).isEqualTo(1)
        }
        drawerState.open()

        // Click on the left-center pixel of the drawer
        rule.onNodeWithTag(bottomDrawerTag).performGesture {
            click(centerLeft)
        }

        rule.runOnIdle {
            assertThat(bodyClicks).isEqualTo(1)
        }
        drawerState.expand()

        // Click on the left-center pixel of the drawer once again in a new state
        rule.onNodeWithTag(bottomDrawerTag).performGesture {
            click(centerLeft)
        }

        rule.runOnIdle {
            assertThat(bodyClicks).isEqualTo(1)
        }
    }

    @Test
    @LargeTest
    fun bottomDrawer_openBySwipe_shortDrawer(): Unit = runBlocking {
        val contentTag = "contentTestTag"
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(
                        Modifier.height(shortBottomDrawerHeight).testTag(bottomDrawerTag)
                    )
                },
                content = { Box(Modifier.fillMaxSize().testTag(contentTag)) }
            )
        }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        }

        rule.onNodeWithTag(contentTag)
            .performGesture { swipeUp() }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Expanded)
        }

        rule.onNodeWithTag(bottomDrawerTag)
            .performGesture { swipeDown() }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        }
    }

    @Test
    @LargeTest
    fun bottomDrawer_expandBySwipe_tallDrawer(): Unit = runBlocking {
        val contentTag = "contentTestTag"
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(
                        Modifier.fillMaxSize().testTag(bottomDrawerTag)
                    )
                },
                content = { Box(Modifier.fillMaxSize().testTag(contentTag)) }
            )
        }

        val isLandscape = rule.rootWidth() > rule.rootHeight()
        val peekHeight = with(rule.density) { rule.rootHeight().toPx() / 2 }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        }

        rule.onNodeWithTag(contentTag)
            .performGesture { swipeUp(endY = peekHeight) }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(
                if (isLandscape) BottomDrawerValue.Expanded else BottomDrawerValue.Open
            )
        }

        rule.onNodeWithTag(bottomDrawerTag)
            .performGesture { swipeUp() }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Expanded)
        }

        rule.onNodeWithTag(bottomDrawerTag)
            .performGesture { swipeDown(endY = peekHeight) }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(
                if (isLandscape) BottomDrawerValue.Closed else BottomDrawerValue.Open
            )
        }

        rule.onNodeWithTag(bottomDrawerTag)
            .performGesture { swipeDown() }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        }
    }

    @Test
    fun bottomDrawer_openBySwipe_onBodyContent(): Unit = runBlocking {
        val contentTag = "contentTestTag"
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = { Box(Modifier.height(shortBottomDrawerHeight)) },
                content = { Box(Modifier.fillMaxSize().testTag(contentTag)) }
            )
        }

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        }

        rule.onNodeWithTag(contentTag)
            .performGesture { swipeUp() }

        advanceClock()

        rule.runOnIdle {
            assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Expanded)
        }
    }

    @Test
    fun bottomDrawer_hasDismissAction_whenExpanded(): Unit = runBlocking {
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Expanded)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val height = rule.rootHeight()
        rule.onNodeWithTag(bottomDrawerTag).onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)

        advanceClock()

        rule.onNodeWithTag(bottomDrawerTag)
            .assertTopPositionInRootIsEqualTo(height)
    }

    @Test
    @LargeTest
    fun bottomDrawer_noDismissActionWhenClosed_hasDissmissActionWhenOpen(): Unit = runBlocking {
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        // Drawer should start in closed state and have no dismiss action
        assertThat(drawerState.currentValue).isEqualTo(BottomDrawerValue.Closed)
        rule.onNodeWithTag(bottomDrawerTag, useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Dismiss))

        // When the drawer state is set to Open or Expanded
        drawerState.open()
        assertThat(drawerState.currentValue)
            .isAnyOf(BottomDrawerValue.Open, BottomDrawerValue.Expanded)
        // Then the drawer should be opened and have dismiss action
        rule.onNodeWithTag(bottomDrawerTag, useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))

        // When the drawer state is set to Closed using dismiss action
        rule.onNodeWithTag(bottomDrawerTag, useUnmergedTree = true)
            .onParent()
            .performSemanticsAction(SemanticsActions.Dismiss)
        // Then the drawer should be closed and have no dismiss action
        rule.onNodeWithTag(bottomDrawerTag, useUnmergedTree = true)
            .onParent()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Dismiss))
    }

    @Test
    @LargeTest
    fun bottomDrawer_openAndClose_shortDrawer(): Unit = runBlocking {
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.height(shortBottomDrawerHeight).testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val height = rule.rootHeight()
        val topWhenOpened = height - shortBottomDrawerHeight
        val topWhenExpanded = topWhenOpened
        val topWhenClosed = height

        // Drawer should start in closed state
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenClosed)

        // When the drawer state is set to Opened
        drawerState.open()
        // Then the drawer should be opened
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenOpened)

        // When the drawer state is set to Expanded
        drawerState.expand()
        // Then the drawer should be expanded
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenExpanded)

        // When the drawer state is set to Closed
        drawerState.close()
        // Then the drawer should be closed
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenClosed)
    }

    @Test
    @LargeTest
    fun bottomDrawer_openAndClose_tallDrawer(): Unit = runBlocking {
        lateinit var drawerState: BottomDrawerState
        rule.setMaterialContent {
            drawerState = rememberBottomDrawerState(BottomDrawerValue.Closed)
            BottomDrawer(
                drawerState = drawerState,
                drawerContent = {
                    Box(Modifier.fillMaxSize().testTag(bottomDrawerTag))
                },
                content = {}
            )
        }

        val width = rule.rootWidth()
        val height = rule.rootHeight()
        val topWhenOpened = if (width > height) 0.dp else (height / 2)
        val topWhenExpanded = 0.dp
        val topWhenClosed = height

        // Drawer should start in closed state
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenClosed)

        // When the drawer state is set to Opened
        drawerState.open()
        // Then the drawer should be opened
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenOpened)

        // When the drawer state is set to Expanded
        drawerState.expand()
        // Then the drawer should be expanded
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenExpanded)

        // When the drawer state is set to Closed
        drawerState.close()
        // Then the drawer should be closed
        rule.onNodeWithTag(bottomDrawerTag).assertTopPositionInRootIsEqualTo(topWhenClosed)
    }

    /**
     * Performs a swipe up gesture on the associated node. The gesture starts at [startY] and
     * ends at [endY].
     *
     * @param startY The start position of the gesture. By default, this is slightly above the
     * bottom of the associated node.
     * @param endY The end position of the gesture. By default, this is the top of the associated
     * node.
     */
    private fun GestureScope.swipeUp(
        startY: Float = (visibleSize.height * (1 - edgeFuzzFactor)).roundToInt().toFloat(),
        endY: Float = 0.0f
    ) {
        require(startY >= endY) {
            "Start position $startY needs to be equal or bigger than end position $endY"
        }
        val x = center.x
        val start = Offset(x, startY)
        val end = Offset(x, endY)
        swipe(start, end, 200)
    }

    /**
     * Performs a swipe down gesture on the associated node. The gesture starts at [startY] and
     * ends at [endY].
     *
     * @param startY The start position of the gesture. By default, this is slightly below the
     * top of the associated node.
     * @param endY The end position of the gesture. By default, this is the bottom of the associated
     * node.
     */
    private fun GestureScope.swipeDown(
        startY: Float = (visibleSize.height * edgeFuzzFactor).roundToInt().toFloat(),
        endY: Float = visibleSize.height.toFloat()
    ) {
        require(endY >= startY) {
            "End position $endY needs to be equal or bigger than start position $startY"
        }
        val x = center.x
        val start = Offset(x, startY)
        val end = Offset(x, endY)
        swipe(start, end, 200)
    }

    /**
     * The distance of a swipe's start position from the node's edge, in terms of the node's length.
     * We do not start the swipe exactly on the node's edge, but somewhat more inward, since swiping
     * from the exact edge may behave in an unexpected way (e.g. may open a navigation drawer).
     */
    private val edgeFuzzFactor = 0.083f
}
