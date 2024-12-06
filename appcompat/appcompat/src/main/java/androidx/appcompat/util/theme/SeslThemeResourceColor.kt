/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.appcompat.util.theme

import android.content.Context
import androidx.appcompat.util.SeslMisc

abstract class SeslThemeResourceColor private constructor() {

    abstract class ResourceColor {
        abstract fun getColor(context: Context): Int
    }

    data class OpenThemeResourceColor(
        val defaultThemeResource: ThemeResourceColor,
        val openThemeResource: ThemeResourceColor
    ) : ResourceColor() {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is OpenThemeResourceColor) {
                return false
            }
            return defaultThemeResource == other.defaultThemeResource
                && openThemeResource == other.openThemeResource
        }

        override fun getColor(context: Context): Int =
            if (SeslMisc.isDefaultTheme(context)) {
                defaultThemeResource.getColor(context)
            } else openThemeResource.getColor(context)

        override fun hashCode(): Int =
            openThemeResource.hashCode() + (defaultThemeResource.hashCode() * 31)


        override fun toString(): String =
            "OpenThemeResourceColor(defaultThemeResource=$defaultThemeResource, openThemeResource=$openThemeResource)"

        companion object {
            fun copydefault(
                originalColor: OpenThemeResourceColor,
                newDefaultColor: ThemeResourceColor,
                newOpenColor: ThemeResourceColor,
                flags: Int
            ): OpenThemeResourceColor {
                val defaultColor = if ((flags and 1) != 0) {
                    originalColor.defaultThemeResource
                } else {
                    newDefaultColor
                }

                val openColor = if ((flags and 2) != 0) {
                    originalColor.openThemeResource
                } else {
                    newOpenColor
                }

                return originalColor.copy(defaultColor, openColor)
            }
        }
    }

    data class ThemeResourceColor @JvmOverloads constructor(
        val lightThemeResId: Int,
        val darkThemeResId: Int = lightThemeResId
    ) : ResourceColor() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ThemeResourceColor) return false
            return this.lightThemeResId == other.lightThemeResId && darkThemeResId == other.darkThemeResId
        }

        override fun getColor(context: Context): Int =
            if (SeslMisc.isLightTheme(context)) this.lightThemeResId else this.darkThemeResId

        override fun hashCode(): Int =
             Integer.hashCode(this.darkThemeResId) + (Integer.hashCode(lightThemeResId) * 31)

        override fun toString(): String =
            "ThemeResourceColor(lightThemeResId=$lightThemeResId, darkThemeResId=$darkThemeResId)"

        companion object {
            fun copydefault(
                original: ThemeResourceColor,
                lightResId: Int,
                darkResId: Int,
                flags: Int
            ): ThemeResourceColor {
                val finalLightResId = if ((flags and 1) != 0) original.lightThemeResId else lightResId
                val finalDarkResId = if ((flags and 2) != 0) original.darkThemeResId else darkResId
                return original.copy(finalLightResId, finalDarkResId)
            }
        }
    }
}