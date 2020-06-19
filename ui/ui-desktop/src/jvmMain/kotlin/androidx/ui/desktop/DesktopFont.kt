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
package androidx.ui.desktop

import org.jetbrains.skija.SkTypeface
import androidx.ui.text.font.Font as uiFont
import androidx.ui.text.font.FontWeight
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.FontListFontFamily
import org.jetbrains.skija.FontCollection
import org.jetbrains.skija.FontManager
import org.jetbrains.skija.TypefaceFontProvider
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File

data class Font(
    val alias: String,
    val path: String,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal
) : uiFont

fun font(
    alias: String,
    path: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): Font = Font(alias, path, weight, style)

class FontLoader() : uiFont.ResourceLoader {
    val fonts = FontCollection()
    private val fontProvider = TypefaceFontProvider()

    init {
        fonts.setDefaultFontManager(FontManager.getDefault())
        fonts.setAssetFontManager(fontProvider)
    }

    fun ensureRegistered(fontFamily: FontFamily): List<String> =
        when (fontFamily) {
            is FontListFontFamily -> fontFamily.fonts.map { load(it) }
            FontFamily.Default -> listOf()
            else -> throw IllegalArgumentException("Unknown font family type: $fontFamily")
        }

    private val registered = mutableSetOf<String>()
    override fun load(font: uiFont): String {
        when (font) {
            is Font -> {
                synchronized(this) {
                    if (!registered.contains(font.alias)) {
                        val typeface = typefaceResource(font.path)
                        fontProvider.registerTypeface(typeface, font.alias)
                        registered.add(font.alias)
                    }
                }
                return font.alias
            }
            else -> throw IllegalArgumentException("Unknown font type: $font")
        }
    }
}

// TODO: get fontFamily from loaded typeface via SkTypeface.getFamilyName
private fun typefaceResource(resourcePath: String): SkTypeface {
    val path = getFontPathAsString(resourcePath)
    return SkTypeface.makeFromFile(path, 0)
}

// TODO: add to skija an ability to load typefaces from memory
fun getFontPathAsString(resourcePath: String): String {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "compose").apply {
        mkdirs()
        deleteOnExit()
    }
    val tempFile = File(tempDir, resourcePath).apply {
        deleteOnExit()
    }
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
    if (stream == null) throw Error("Cannot find font $resourcePath")
    Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return tempFile.absolutePath
}