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

package androidx.compose.ui

import androidx.compose.ui.platform.InspectableValue
import com.google.common.truth.Truth
import org.junit.Test

class ZIndexModifierTest {
    @Test
    fun testInspectable() {
        val modifier = Modifier.zIndex(3f) as InspectableValue
        Truth.assertThat(modifier.nameFallback).isEqualTo("zIndex")
        Truth.assertThat(modifier.valueOverride).isEqualTo(3.0f)
        Truth.assertThat(modifier.inspectableElements.toList()).isEmpty()
    }
}