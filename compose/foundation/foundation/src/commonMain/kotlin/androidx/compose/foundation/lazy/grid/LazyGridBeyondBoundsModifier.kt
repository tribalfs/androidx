/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.lazy.grid

import androidx.compose.foundation.lazy.layout.LazyLayoutBeyondBoundsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun rememberLazyGridBeyondBoundsState(state: LazyGridState): LazyLayoutBeyondBoundsState {
    return remember(state) { LazyGridBeyondBoundsState(state) }
}

internal class LazyGridBeyondBoundsState(
    val state: LazyGridState,
) : LazyLayoutBeyondBoundsState {

    override val itemCount: Int
        get() = state.layoutInfo.totalItemsCount

    override val hasVisibleItems: Boolean
        get() = state.layoutInfo.visibleItemsInfo.isNotEmpty()

    override val firstPlacedIndex: Int
        get() = state.firstVisibleItemIndex

    override val lastPlacedIndex: Int
        get() = state.layoutInfo.visibleItemsInfo.last().index
}
