/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

@Immutable
@kotlin.jvm.JvmInline
internal value class Strings private constructor(@Suppress("unused") private val value: Int) {
    companion object {
        val NavigationMenu = Strings(0)
        val CloseDrawer = Strings(1)
        val CloseSheet = Strings(2)
        val DefaultErrorMessage = Strings(3)
        val ExposedDropdownMenu = Strings(4)
        val SliderRangeStart = Strings(5)
        val SliderRangeEnd = Strings(6)
        val Dialog = Strings(7)
        val MenuExpanded = Strings(8)
        val MenuCollapsed = Strings(9)
        val SnackbarDismiss = Strings(10)
        val SearchBarSearch = Strings(11)
        val SuggestionsAvailable = Strings(12)
        val DatePickerTitle = Strings(13)
        val DatePickerHeadline = Strings(14)
        val DatePickerYearPickerPaneTitle = Strings(15)
        val DatePickerSwitchToYearSelection = Strings(16)
        val DatePickerSwitchToDaySelection = Strings(17)
        val DatePickerSwitchToNextMonth = Strings(18)
        val DatePickerSwitchToPreviousMonth = Strings(19)
        val DatePickerNavigateToYearDescription = Strings(20)
        val DatePickerHeadlineDescription = Strings(21)
        val DatePickerNoSelectionDescription = Strings(22)
        val DateInputTitle = Strings(23)
        val DateInputHeadline = Strings(24)
        val DateInputLabel = Strings(25)
        val DateInputHeadlineDescription = Strings(26)
        val DateInputNoInputHeadlineDescription = Strings(27)
        val DateInputInvalidNotAllowed = Strings(28)
        val DateInputInvalidForPattern = Strings(29)
        val DateInputInvalidYearRange = Strings(30)
        val TooltipLongPressLabel = Strings(31)
    }
}

@Composable
@ReadOnlyComposable
internal expect fun getString(string: Strings): String

@Composable
@ReadOnlyComposable
internal expect fun getString(string: Strings, vararg formatArgs: Any): String