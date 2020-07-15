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

package androidx.ui.foundation

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.lazy.LazyColumnItems

@Composable
@Deprecated(
    "AdapterList was renamed to LazyColumnItems",
    replaceWith = ReplaceWith(
        "LazyColumnItems(data, modifier, itemCallback)",
        "androidx.ui.foundation.lazy.LazyColumnItems"
    )
)
fun <T> AdapterList(
    data: List<T>,
    modifier: Modifier = Modifier,
    itemCallback: @Composable (T) -> Unit
) {
    LazyColumnItems(items = data, modifier = modifier, itemContent = itemCallback)
}
