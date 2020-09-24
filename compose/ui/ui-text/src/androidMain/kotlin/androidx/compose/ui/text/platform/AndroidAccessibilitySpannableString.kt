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

package androidx.compose.ui.text.platform

import android.text.SpannableString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.platform.extensions.setLocaleList
import androidx.compose.ui.util.fastForEach

/**
 * Convert an AnnotatedString into SpannableString for Android text to speech support.
 */
@InternalTextApi
fun AnnotatedString.toAccessibilitySpannableString(): SpannableString {
    val spannableString = SpannableString(text)
    spanStyles.fastForEach { (style, start, end) ->
        spannableString.setLocaleList(style.localeList, start, end)
    }

    return spannableString
}