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

package androidx.ui.core.texttoolbar

import android.os.Build
import android.view.ActionMode
import android.view.View
import androidx.ui.core.texttoolbar.actionmodecallback.FloatingTextActionModeCallback
import androidx.ui.core.texttoolbar.actionmodecallback.PrimaryTextActionModeCallback
import androidx.ui.core.texttoolbar.actionmodecallback.TextActionModeCallback
import androidx.ui.geometry.Rect
import androidx.ui.text.AnnotatedString

/**
 * Android implementation for [TextToolbar].
 */
internal class AndroidTextToolbar(private val view: View) : TextToolbar {
    override fun showCopyMenu(rect: Rect, text: AnnotatedString) {
        if (Build.VERSION.SDK_INT >= 23) {
            val actionModeCallback =
                FloatingTextActionModeCallback(
                    TextActionModeCallback(view)
                )
            actionModeCallback.setRect(rect)
            actionModeCallback.setText(text)
            view.startActionMode(
                actionModeCallback,
                ActionMode.TYPE_FLOATING
            )
        } else {
            val actionModeCallback =
                PrimaryTextActionModeCallback(
                    TextActionModeCallback(view)
                )
            actionModeCallback.setText(text)
            view.startActionMode(actionModeCallback)
        }
    }
}
