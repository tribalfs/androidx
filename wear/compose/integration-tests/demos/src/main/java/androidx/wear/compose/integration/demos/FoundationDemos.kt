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

package androidx.wear.compose.integration.demos

import androidx.wear.compose.foundation.samples.CurvedAndNormalText
import androidx.wear.compose.foundation.samples.CurvedBottomLayout
import androidx.wear.compose.foundation.samples.CurvedBackground
import androidx.wear.compose.foundation.samples.CurvedFixedSize
import androidx.wear.compose.foundation.samples.CurvedFontWeight
import androidx.wear.compose.foundation.samples.CurvedFonts
import androidx.wear.compose.foundation.samples.CurvedRowAndColumn
import androidx.wear.compose.foundation.samples.CurvedWeight
import androidx.wear.compose.foundation.samples.OversizeComposable
import androidx.wear.compose.foundation.samples.SimpleCurvedWorld

val WearFoundationDemos = DemoCategory(
    "Foundation",
    listOf(
        DemoCategory("CurvedLayout", listOf(
            ComposableDemo("Curved Row") { CurvedWorldDemo() },
            ComposableDemo("Curved Row and Column") { CurvedRowAndColumn() },
            ComposableDemo("Simple") { SimpleCurvedWorld() },
            ComposableDemo("Alignment") { CurvedRowAlignmentDemo() },
            ComposableDemo("Curved Text") { BasicCurvedTextDemo() },
            ComposableDemo("Curved and Normal Text") { CurvedAndNormalText() },
            ComposableDemo("Fixed size") { CurvedFixedSize() },
            ComposableDemo("Oversize composable") { OversizeComposable() },
            ComposableDemo("Weights") { CurvedWeight() },
            ComposableDemo("Ellipsis Demo") { CurvedEllipsis() },
            ComposableDemo("Bottom layout") { CurvedBottomLayout() },
            ComposableDemo("Curved layout direction") { CurvedLayoutDirection() },
            ComposableDemo("Background") { CurvedBackground() },
            ComposableDemo("Font Weight") { CurvedFontWeight() },
            ComposableDemo("Fonts") { CurvedFonts() },
        )),
        ComposableDemo("Scrollable Column") { ScrollableColumnDemo() },
        ComposableDemo("Scrollable Row") { ScrollableRowDemo() },
        DemoCategory("Rotary Input", RotaryInputDemos),
    ),
)
