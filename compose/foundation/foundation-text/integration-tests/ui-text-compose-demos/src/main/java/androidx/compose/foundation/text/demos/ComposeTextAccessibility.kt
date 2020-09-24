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

package androidx.compose.foundation.text.demos

import androidx.compose.foundation.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.annotatedString
import androidx.compose.ui.text.intl.LocaleList

@Composable
fun TextAccessibilityDemo() {
    Text(
        text = annotatedString {
            pushStyle(SpanStyle(localeList = LocaleList("en-us")))
            append("Hello!\n")
            pop()
            pushStyle(SpanStyle(localeList = LocaleList("en-gb")))
            append("Hello!\n")
            pop()
            pushStyle(SpanStyle(localeList = LocaleList("fr")))
            append("Bonjour!\n")
            pop()
            pushStyle(SpanStyle(localeList = LocaleList("tr-TR")))
            append("Merhaba!\n")
            pop()
            pushStyle(SpanStyle(localeList = LocaleList("ja-JP")))
            append("こんにちは!\n")
            pop()
            pushStyle(SpanStyle(localeList = LocaleList("zh")))
            append("你好!\n")
            pop()
        },
        style = TextStyle(fontSize = fontSize8)
    )
}