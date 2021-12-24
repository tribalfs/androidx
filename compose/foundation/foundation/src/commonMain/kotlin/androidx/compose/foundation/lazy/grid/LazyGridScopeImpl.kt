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

package androidx.compose.foundation.lazy.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.GridItemSpan
import androidx.compose.foundation.lazy.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.LazyGridScope
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable

@OptIn(ExperimentalFoundationApi::class)
internal class LazyGridScopeImpl : LazyGridScope {
    internal val intervals = MutableIntervalList<LazyGridIntervalContent>()
    internal var hasCustomSpans = false

    private val DefaultSpan: LazyGridItemSpanScope.(Int) -> GridItemSpan = { GridItemSpan(1) }

    override fun item(
        key: Any?,
        span: (LazyGridItemSpanScope.() -> GridItemSpan)?,
        content: @Composable () -> Unit
    ) {
        intervals.add(
            1,
            LazyGridIntervalContent(
                key = key?.let { { key } },
                span = span?.let { { span() } } ?: DefaultSpan,
                content = { { content() } }
            )
        )
        if (span != null) hasCustomSpans = true
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        span: (LazyGridItemSpanScope.(Int) -> GridItemSpan)?,
        itemContent: @Composable (index: Int) -> Unit
    ) {
        intervals.add(
            count,
            LazyGridIntervalContent(
                key = key,
                span = span ?: DefaultSpan,
                content = { { itemContent(it) } }
            )
        )
        if (span != null) hasCustomSpans = true
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal class LazyGridIntervalContent(
    val key: ((index: Int) -> Any)?,
    val span: LazyGridItemSpanScope.(Int) -> GridItemSpan,
    val content: (Int) -> (@Composable () -> Unit)
)
