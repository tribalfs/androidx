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

package androidx.compose.foundation.demos.text

import android.graphics.Typeface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AmbientContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.fontFamily
import androidx.compose.ui.text.font.typeface

@Composable
fun TypefaceDemo() {
    LazyColumn {
        item {
            TagLine(tag = "Android Typeface")
            AndroidTypefaceDemo()
        }
        item {
            TagLine(tag = "Typeface from FontFamily")
            TypefaceFromFontFamilyDemo()
        }
        item {
            TagLine(tag = "FontFamily from Android Typeface")
            FontFamilyFromAndroidTypeface()
        }
    }
}

@Composable
fun TypefaceFromFontFamilyDemo() {
    val typeface = typeface(AmbientContext.current, FontFamily.Cursive)
    val fontFamily = fontFamily(typeface)
    Text("Hello World", style = TextStyle(fontFamily = fontFamily))
}

@Composable
fun AndroidTypefaceDemo() {
    val fontFamily = fontFamily(typeface(Typeface.DEFAULT_BOLD))
    Text("Hello World", style = TextStyle(fontFamily = fontFamily))
}

@Composable
fun FontFamilyFromAndroidTypeface() {
    val fontFamily = fontFamily(Typeface.MONOSPACE)
    Text("Hello World", style = TextStyle(fontFamily = fontFamily))
}
