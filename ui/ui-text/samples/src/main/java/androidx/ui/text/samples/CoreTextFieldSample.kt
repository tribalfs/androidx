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
import androidx.compose.getValue
import androidx.compose.setValue
import androidx.ui.input.TextFieldValue
import androidx.ui.savedinstancestate.savedInstanceState
import androidx.ui.text.CoreTextField

@Sampled
@Composable
fun CoreTextFieldSample() {
    var value by savedInstanceState(saver = TextFieldValue.Saver) { TextFieldValue() }
    CoreTextField(
        value = value,
        onValueChange = {
            // it is crucial that the update is fed back into CoreTextField in order to
            // see updates on the text
            value = it
        }
    )
}