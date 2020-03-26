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

package androidx.ui.res

import android.content.Context
import android.util.TypedValue
import androidx.annotation.GuardedBy
import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.text.Typeface
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.FontListFontFamily
import androidx.ui.text.font.LoadedFontFamily
import androidx.ui.text.font.ResourceFont
import androidx.ui.text.font.SystemFontFamily
import androidx.ui.text.typefaceFromFontFamily

private val cacheLock = Object()

/**
 * This cache is expected to be used for SystemFontFamily or LoadedFontFamily.
 * FontFamily instance cannot be used as the file based FontFamily.
 */
@GuardedBy("cacheLock")
private val syncLoadedTypefaces = mutableMapOf<FontFamily, Typeface>()

/**
 * Synchronously load an font from [FontFamily].
 *
 * @param fontFamily the fontFamily
 * @return the decoded image data associated with the resource
 */
@Composable
fun fontResource(fontFamily: FontFamily): Typeface {
    val context = ContextAmbient.current
    if (fontFamily is SystemFontFamily || fontFamily is LoadedFontFamily) {
        synchronized(cacheLock) {
            return syncLoadedTypefaces.getOrPut(fontFamily) {
                typefaceFromFontFamily(context, fontFamily)
            }
        }
    } else {
        return typefaceFromFontFamily(context, fontFamily)
    }
}

/**
 * Load the FontFamily in background thread.
 *
 * Until font family loading complete, this function returns deferred Typeface with
 * [PendingResource]. Once loading finishes, recompose is scheduled and this function will return
 * deferred Typeface resource with [LoadedResource] or [FailedResource].
 *
 * @param fontFamily the font family to be loaded
 * @param pendingFontFamily an optional resource to be used during loading instead. Only
 * [FontFamily] that can be loaded synchronously can be used as a pendingFontFamily.
 * @param failedFontFamily an optional resource to be used during loading instead. Only
 * [FontFamily] that can be loaded synchronously can be used as a failedFontFamily.
 * @throws IllegalArgumentException if [FontFamily] other than synchronously loadable ones are
 * passed as an argument of pendingFontFamily or failedFontFamily.
 *
 * @sample androidx.ui.framework.samples.FontResourcesFontFamily
 */
@Composable
fun loadFontResource(
    fontFamily: FontFamily,
    pendingFontFamily: FontFamily? = null,
    failedFontFamily: FontFamily? = null
): DeferredResource<Typeface> {
    val pendingTypeface = if (pendingFontFamily == null) {
        null
    } else if (!pendingFontFamily.canLoadSynchronously) {
        throw IllegalArgumentException(
            "Only FontFamily that can be loaded synchronously can be used as a pendingFontFamily")
    } else {
        synchronized(cacheLock) {
            syncLoadedTypefaces.getOrPut(pendingFontFamily) {
                fontResource(pendingFontFamily)
            }
        }
    }

    val failedTypeface = if (failedFontFamily == null) {
        null
    } else if (!failedFontFamily.canLoadSynchronously) {
        throw IllegalArgumentException(
            "Only FontFamily that can be loaded synchronously can be used as a failedFontFamily")
    } else {
        synchronized(cacheLock) {
            syncLoadedTypefaces.getOrPut(failedFontFamily) {
                fontResource(failedFontFamily)
            }
        }
    }

    return loadFontResource(fontFamily, pendingTypeface, failedTypeface)
}

/**
 * Load the FontFamily in background thread.
 *
 * Until font family loading complete, this function returns deferred Typeface with
 * [PendingResource]. Once loading finishes, recompose is scheduled and this function will return
 * deferred Typeface resource with [LoadedResource] or [FailedResource].
 *
 * @param fontFamily the font family to be loaded
 * @param pendingTypeface an optional resource to be used during loading instead.
 * @param failedTypeface an optional resource to be used during loading instead.
 * @throws IllegalArgumentException if [FontFamily] other than synchronously loadable ones are
 * passed as an argument of pendingFontFamily or failedFontFamily.
 *
 * @sample androidx.ui.framework.samples.FontResourcesTypeface
 */
@Composable
fun loadFontResource(
    fontFamily: FontFamily,
    pendingTypeface: Typeface? = null,
    failedTypeface: Typeface? = null
): DeferredResource<Typeface> {
    if (fontFamily.canLoadSynchronously) {
        val typeface = synchronized(cacheLock) {
            syncLoadedTypefaces.getOrPut(fontFamily) {
                fontResource(fontFamily)
            }
        }
        return DeferredResource(
            pendingResource = pendingTypeface,
            failedResource = failedTypeface).apply {
            loadCompleted(typeface)
        }
    } else {
        if (fontFamily !is FontListFontFamily) {
            // Only FontListFontFamily can be loaded asynchronously at this moment.
            return DeferredResource(
                state = LoadingState.FAILED,
                pendingResource = pendingTypeface,
                failedResource = failedTypeface)
        }
        val context = ContextAmbient.current
        val key = fontFamily.cacheKey(context)
        return loadResource(key, pendingTypeface, failedTypeface) {
            typefaceFromFontFamily(context, fontFamily)
        }
    }
}

internal fun FontListFontFamily.cacheKey(context: Context): String {
    val concatenatedResourcePaths = StringBuilder()
    val value = TypedValue()
    for (font in fonts) {
        when (font) {
            is ResourceFont -> {
                context.resources.getValue(font.resId, value, true)
                concatenatedResourcePaths.append(value.string?.toString())
                Unit // Workaround for ClassCastException due to compiler issue. (b/152448057)
            }
        }
    }
    return concatenatedResourcePaths.toString()
}