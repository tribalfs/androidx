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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.preferredSizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.platform.AndroidOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun ComposeTestRule.setMaterialContent(
    modifier: Modifier = Modifier,
    composable: @Composable () -> Unit
) {
    setContent {
        MaterialTheme {
            Surface(modifier = modifier, content = composable)
        }
    }
}

fun <T> ComposeTestRule.runOnIdleWithDensity(action: Density.() -> T): T {
    return runOnIdle {
        density.action()
    }
}

fun SemanticsNodeInteraction.getFirstBaselinePosition() = getAlignmentLinePosition(FirstBaseline)

fun SemanticsNodeInteraction.getLastBaselinePosition() = getAlignmentLinePosition(LastBaseline)

fun SemanticsNodeInteraction.assertIsSquareWithSize(expectedSize: Dp) =
    assertWidthIsEqualTo(expectedSize).assertHeightIsEqualTo(expectedSize)

fun SemanticsNodeInteraction.assertWidthFillsRoot(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("Failed to assertWidthFillsScreen")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner
    val rootViewWidth = owner.view.width

    with(owner.density) {
        node.boundsInRoot.width.toDp().assertIsEqualTo(rootViewWidth.toDp())
    }
    return this
}

fun ComposeTestRule.rootWidth(): Dp {
    val nodeInteraction = onRoot()
    val node = nodeInteraction.fetchSemanticsNode("Failed to get screen width")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner

    return with(owner.density) {
        owner.view.width.toDp()
    }
}

fun ComposeTestRule.rootHeight(): Dp {
    val nodeInteraction = onRoot()
    val node = nodeInteraction.fetchSemanticsNode("Failed to get screen height")
    @OptIn(ExperimentalLayoutNodeApi::class)
    val owner = node.componentNode.owner as AndroidOwner

    return with(owner.density) {
        owner.view.height.toDp()
    }
}

/**
 * Constant to emulate very big but finite constraints
 */
val BigTestMaxWidth = 5000.dp
val BigTestMaxHeight = 5000.dp

fun ComposeTestRule.setMaterialContentForSizeAssertions(
    parentMaxWidth: Dp = BigTestMaxWidth,
    parentMaxHeight: Dp = BigTestMaxHeight,
    // TODO : figure out better way to make it flexible
    content: @Composable () -> Unit
): SemanticsNodeInteraction {
    setContent {
        MaterialTheme {
            Surface {
                Box {
                    Box(
                        Modifier.preferredSizeIn(
                            maxWidth = parentMaxWidth,
                            maxHeight = parentMaxHeight
                        ).testTag("containerForSizeAssertion")
                    ) {
                        content()
                    }
                }
            }
        }
    }

    return onNodeWithTag("containerForSizeAssertion")
}
