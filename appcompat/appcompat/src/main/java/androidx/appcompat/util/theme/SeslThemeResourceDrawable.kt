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

abstract class SeslThemeResourceDrawable private constructor() {

    abstract class ResourceDrawable {
        abstract fun getDrawable(context: Context): Int
    }

    data class OpenThemeResourceDrawable(
        val defaultThemeResource: ThemeResourceDrawable,
        val openThemeResource: ThemeResourceDrawable
    ) : ResourceDrawable() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OpenThemeResourceDrawable) return false

            return defaultThemeResource == other.defaultThemeResource
                &&  openThemeResource == other.openThemeResource
        }

        override fun getDrawable(context: Context): Int =
            if (SeslMisc.isDefaultTheme(context)) {
                defaultThemeResource.getDrawable(context)
            } else openThemeResource.getDrawable(context)


        override fun hashCode(): Int =
            openThemeResource.hashCode() + (defaultThemeResource.hashCode() * 31)

        override fun toString(): String =
            "OpenThemeResourceDrawable(defaultThemeResource=$defaultThemeResource, openThemeResource=$openThemeResource )"

        companion object {
            fun copydefault(
                originalDrawable: OpenThemeResourceDrawable,
                newDefaultDrawable: ThemeResourceDrawable,
                newOpenDrawable: ThemeResourceDrawable,
                flags: Int
            ): OpenThemeResourceDrawable {
                val defaultDrawable = if ((flags and 1) != 0) {
                    originalDrawable.defaultThemeResource
                } else {
                    newDefaultDrawable
                }

                val openDrawable = if ((flags and 2) != 0) {
                    originalDrawable.openThemeResource
                } else {
                    newOpenDrawable
                }

                return originalDrawable.copy(defaultDrawable, openDrawable)
            }
        }
    }

    data class ThemeResourceDrawable @JvmOverloads constructor(
        val lightThemeResId: Int,
        val darkThemeResId: Int = lightThemeResId
    ) : ResourceDrawable() {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is ThemeResourceDrawable) {
                return false
            }
            return this.lightThemeResId == other.lightThemeResId && this.darkThemeResId == other.darkThemeResId
        }

        override fun getDrawable(context: Context): Int =
            if (SeslMisc.isLightTheme(context)) this.lightThemeResId else this.darkThemeResId


        override fun hashCode(): Int =
            Integer.hashCode(this.darkThemeResId) + (Integer.hashCode(lightThemeResId) * 31)

        override fun toString(): String =
            "ThemeResourceDrawable(lightThemeResId=$lightThemeResId, darkThemeResId=$darkThemeResId)"

        companion object {
            fun copydefault(
                originalDrawable: ThemeResourceDrawable,
                newLightResId: Int,
                newDarkResId: Int,
                flags: Int
            ): ThemeResourceDrawable {
                val lightResId = if ((flags and 1) != 0) {
                    originalDrawable.lightThemeResId
                } else {
                    newLightResId
                }

                val darkResId = if ((flags and 2) != 0) {
                    originalDrawable.darkThemeResId
                } else {
                    newDarkResId
                }

                return originalDrawable.copy(lightResId, darkResId)
            }
        }
    }
}