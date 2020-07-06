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

package androidx.ui.core.keyinput

import com.google.common.truth.Truth
import android.view.KeyEvent as AndroidKeyEvent

/**
 * The [KeyEvent][KeyEvent2] is usually created by the system. This function creates an instance
 * of [KeyEvent][KeyEvent2] that can be used in tests.
 */
fun keyEvent(key: Key, keyEventType: KeyEventType): KeyEvent2 {
    val action = when (keyEventType) {
        KeyEventType.KeyDown -> android.view.KeyEvent.ACTION_DOWN
        KeyEventType.KeyUp -> android.view.KeyEvent.ACTION_UP
        KeyEventType.Unknown -> error("Unknown key event type")
    }
    return KeyEventAndroid(AndroidKeyEvent(action, key.keyCode))
}

/** KeyEvent2 is an inline class that wraps android's KeyEvent class.
 *  Android's KeyEvent does not define an equals() function
 *  [KeyEventAndroid] inline classes do not allow
 *  overriding the equals() function.  So we use this util function to compare KeyEvents.
 */
fun KeyEvent2.assertEqualTo(expected: KeyEvent2) {
    Truth.assertThat(key).isEqualTo(expected.key)
    Truth.assertThat(type).isEqualTo(expected.type)
}
