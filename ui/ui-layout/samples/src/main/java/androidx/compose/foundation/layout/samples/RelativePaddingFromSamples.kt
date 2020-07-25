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

package androidx.compose.foundation.layout.samples

import androidx.annotation.Sampled
import androidx.compose.runtime.Composable
import androidx.ui.core.Modifier
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.relativePaddingFrom
import androidx.compose.foundation.text.FirstBaseline
import androidx.compose.ui.unit.dp

@Sampled
@Composable
fun RelativePaddingFromSample() {
    Text(
        text = "This is an example.",
        modifier = Modifier.relativePaddingFrom(FirstBaseline, before = 30.dp)
    )
}