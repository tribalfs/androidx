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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.Handle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.RequiresDevice
import androidx.test.filters.SdkSuppress
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@SdkSuppress(minSdkVersion = 28)
@RunWith(AndroidJUnit4::class)
internal class SelectionContainerMagnifierTest : AbstractSelectionMagnifierTests() {

    @Composable
    override fun TestContent(
        text: String,
        modifier: Modifier,
        style: TextStyle,
        onTextLayout: (TextLayoutResult) -> Unit,
        maxLines: Int
    ) {
        SelectionContainer(modifier) {
            BasicText(text, style = style, onTextLayout = onTextLayout, maxLines = maxLines)
        }
    }

    @RequiresDevice // b/264702195
    @Test
    fun magnifier_goesToLastLine_whenSelectionEndDraggedBelowTextBounds_whenTextOverflowed() {
        checkMagnifierAsHandleGoesOutOfBoundsUsingMaxLines(Handle.SelectionEnd)
    }

    @RequiresDevice // b/264702195
    @Test
    fun magnifier_hidden_whenSelectionStartDraggedBelowTextBounds_whenTextOverflowed() {
        checkMagnifierAsHandleGoesOutOfBoundsUsingMaxLines(Handle.SelectionStart)
    }
}
