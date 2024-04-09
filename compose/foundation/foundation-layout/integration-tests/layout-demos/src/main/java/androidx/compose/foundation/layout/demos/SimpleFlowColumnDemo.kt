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

package androidx.compose.foundation.layout.demos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.samples.SimpleFlowColumn
import androidx.compose.foundation.layout.samples.SimpleFlowColumnMaxLinesDynamicSeeMore
import androidx.compose.foundation.layout.samples.SimpleFlowColumnMaxLinesWithSeeMore
import androidx.compose.foundation.layout.samples.SimpleFlowColumnWithMaxWidth
import androidx.compose.foundation.layout.samples.SimpleFlowColumn_EqualWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SimpleFlowColumnDemo() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SimpleFlowColumn()
        SimpleFlowColumnMaxLinesWithSeeMore()
        SimpleFlowColumnMaxLinesDynamicSeeMore()
        SimpleFlowColumnWithMaxWidth()
        SimpleFlowColumn_EqualWidth()
    }
}