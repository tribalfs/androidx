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

package androidx.ui.material

import androidx.ui.core.CurrentTextStyleProvider
import androidx.ui.engine.text.FontWeight
import androidx.ui.engine.text.font.FontFamily
import androidx.ui.painting.Color
import androidx.ui.painting.TextStyle
import com.google.r4a.Ambient
import com.google.r4a.Children
import com.google.r4a.Component
import com.google.r4a.composer

/**
 * This Component defines the styling principles from the Material design specification. It must be
 * present within a hierarchy of components that includes Material components, as it defines key
 * values such as base colors and typography.
 *
 * By default, it defines the colors as specified in the Color theme creation spec
 * (https://material.io/design/color/the-color-system.html#color-theme-creation) and the typography
 * defined in the Type Scale spec
 * (https://material.io/design/typography/the-type-system.html#type-scale).
 *
 * All values may be overriden by providing this component with the [colors] and [typography]
 * attributes. Use this to configure the overall theme of your application.
 */
class MaterialTheme(
    @Children
    val children: () -> Unit
) : Component() {

    var colors: MaterialColors = MaterialColors()
    var typography: MaterialTypography = MaterialTypography()

    override fun compose() {
        <Colors.Provider value=colors>
            <Typography.Provider value=typography>
                <CurrentTextStyleProvider value=typography.body1>
                    <children />
                </CurrentTextStyleProvider>
            </Typography.Provider>
        </Colors.Provider>
    }
}

/**
 * This Ambient holds on to the current definition of colors for this application as described
 * by the Material spec. You can read the values in it when creating custom components that want
 * to use Material colors, as well as override the values when you want to re-style a part of your
 * hierarchy.
 */
val Colors = Ambient<MaterialColors>("colors") { error("No colors found!") }

/**
 * This Ambient holds on to the current definiton of typography for this application as described
 * by the Material spec.  You can read the values in it when creating custom components that want
 * to use Material types, as well as override the values when you want to re-style a part of your
 * hierarchy. Material components related to text such as [H1] will refer to this Ambient to obtain
 * the values with which to style text.
 */
val Typography = Ambient<MaterialTypography>("typography") { error("No typography found!") }

/**
 * Data class holding color values as defined by the Material specification
 * (https://material.io/design/color/the-color-system.html#color-theme-creation).
 */
data class MaterialColors(
    /**
     * The primary color is the color displayed most frequently across your app’s screens and
     * components.
     */
    val primary: Color = Color(0xFF6200EE.toInt()),
    /**
     * The primary variant is used to distinguish two elements of the app using the primary color,
     * such as the top app bar and the system bar.
     */
    val primaryVariant: Color = Color(0xFF3700B3.toInt()),
    /**
     * The secondary color provides more ways to accent and distinguish your product.
     * Secondary colors are best for:
     * <ul>
     *     <li>Floating action buttons</li>
     *     <li>Selection controls, like sliders and switches</li>
     *     <li>Highlighting selected text</li>
     *     <li>Progress bars</li>
     *     <li>Links and headlines</li>
     * </ul>
     */
    val secondary: Color = Color(0xFF03DAC6.toInt()),
    /**
     * The secondary variant is used to distinguish two elements of the app using the secondary
     * color.
     */
    val secondaryVariant: Color = Color(0xFF018786.toInt()),
    /**
     * The background color appears behind scrollable content.
     */
    val background: Color = Color(0xFFFFFFFF.toInt()),
    /**
     * The surface color is used on surfaces of components, such as cards, sheets and menus.
     */
    val surface: Color = Color(0xFFFFFFFF.toInt()),
    /**
     * The error color is used to indicate error within components, such as text fields.
     */
    val error: Color = Color(0xFFB00020.toInt()),
    /**
     * Color used for text and icons displayed on top of the primary color.
     */
    val onPrimary: Color = Color(0xFFFFFFFF.toInt()),
    /**
     * Color used for text and icons displayed on top of the secondary color.
     */
    val onSecondary: Color = Color(0xFF000000.toInt()),
    /**
     * Color used for text and icons displayed on top of the background color.
     */
    val onBackground: Color = Color(0xFF000000.toInt()),
    /**
     * Color used for text and icons displayed on top of the surface color.
     */
    val onSurface: Color = Color(0xFF000000.toInt()),
    /**
     * Color used for text and icons displayed on top of the error color.
     */
    val onError: Color = Color(0xFFFFFFFF.toInt())
)

/**
 * Data class holding typography definitions as defined by the Material specification
 * (https://material.io/design/typography/the-type-system.html#type-scale).
 */
data class MaterialTypography(
    // TODO(clara): case
    // TODO(clara): letter spacing (specs don't match)
    // TODO(clara): b/123001228 need a font abstraction layer
    // TODO(clara): fontSize should be a Dimension, translating here will loose context changes
    val h1: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.w100,
        fontSize = 96f),
    val h2: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.w100,
        fontSize = 60f),
    val h3: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 48f),
    val h4: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 34f),
    val h5: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 24f),
    val h6: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.w500,
        fontSize = 20f),
    val subtitle1: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 16f),
    val subtitle2: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.w500,
        fontSize = 14f),
    val body1: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 16f),
    val body2: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 14f),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.w500,
        fontSize = 14f),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 12f),
    val overline: TextStyle = TextStyle(
        fontFamily = FontFamily("Roboto"),
        fontWeight = FontWeight.normal,
        fontSize = 10f)
)
