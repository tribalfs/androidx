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

package androidx.ui.text.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.foundation.Text
import androidx.ui.text.AnnotatedString
import androidx.ui.text.SpanStyle
import androidx.ui.text.TextStyle
import androidx.ui.text.style.BaselineShift
import androidx.ui.text.withStyle
import androidx.ui.unit.sp

@Sampled
@Composable
fun BaselineShiftSample() {
    Text(
        style = TextStyle(fontSize = 20.sp),
        text = AnnotatedString {
            append(text = "Hello")
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 16.sp)) {
                append("superscript")
                withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                    append("subscript")
                }
            }
        }
    )
}

@Sampled
@Composable
fun BaselineShiftAnnotatedStringSample() {
    val annotatedString = AnnotatedString {
        append("Text ")
        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
            append("Demo")
        }
    }
    Text(text = annotatedString)
}
