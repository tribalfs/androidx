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

package androidx.compose.material3.catalog.library.model

import androidx.annotation.DrawableRes
import androidx.compose.material3.catalog.library.R
import androidx.compose.material3.catalog.library.util.Material3SourceUrl

data class Component(
    val id: Int,
    val name: String,
    val description: String,
    @DrawableRes
    val icon: Int = R.drawable.ic_component,
    val tintIcon: Boolean = false,
    val guidelinesUrl: String,
    val docsUrl: String,
    val sourceUrl: String,
    val examples: List<Example>
)

// Components are ordered alphabetically by name.

private val Button = Component(
    id = 1,
    name = "Button",
    description = "Buttons allow users to take actions, and make choices, with a single tap.",
    tintIcon = true,
    guidelinesUrl = "", // No guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/Button.kt",
    examples = ButtonsExamples,
)

private val Color = Component(
    id = 2,
    name = "Color",
    description = "Material You colors",
    // No color icon
    tintIcon = true,
    guidelinesUrl = "", // No guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/ColorScheme.kt",
    examples = ColorExamples
)

private val Dialog = Component(
    id = 3,
    name = "Dialog",
    description = "Material 3 basic dialogs",
    // No dialog icon
    tintIcon = true,
    guidelinesUrl = "", // No guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/AlertDialog.kt",
    examples = DialogExamples
)

private val FloatingActionButtons = Component(
    id = 4,
    name = "Floating action button",
    description = "A floating action button (FAB) represents the primary action of a screen.",
    tintIcon = true,
    guidelinesUrl = "", // No guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/FloatingActionButton.kt",
    examples = FloatingActionButtonsExamples,
)

private val NavigationBar = Component(
    id = 5,
    name = "Navigation bar",
    description = "Material You navigation bar",
    tintIcon = true,
    guidelinesUrl = "", // No  guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/NavigationBar.kt",
    examples = NavigationBarExamples
)

private val TopAppBar = Component(
    id = 6,
    name = "Top app bar",
    description = "Material You top app bar",
    // No color icon
    tintIcon = true,
    guidelinesUrl = "", // No guidelines yet
    docsUrl = "", // No docs yet
    sourceUrl = "$Material3SourceUrl/AppBar.kt",
    examples = TopAppBarExamples
)

// Next component ID = 7

/** Components for the catalog, ordered alphabetically by name. */
val Components = listOf(
    Button,
    FloatingActionButtons,
    Color,
    Dialog,
    NavigationBar,
    TopAppBar,
)
