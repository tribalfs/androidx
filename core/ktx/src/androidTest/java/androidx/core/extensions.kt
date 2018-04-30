/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.core

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Xml
import androidx.annotation.LayoutRes
import org.xmlpull.v1.XmlPullParser

@SuppressLint("ResourceType")
fun Context.getAttributeSet(@LayoutRes layoutId: Int): AttributeSet {
    val parser = resources.getXml(layoutId)
    var type = parser.next()
    while (type != XmlPullParser.START_TAG) {
        type = parser.next()
    }
    return Xml.asAttributeSet(parser)
}
