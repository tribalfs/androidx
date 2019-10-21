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

package androidx.ui.res

import android.util.TypedValue
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import androidx.compose.ambient
import androidx.compose.effectOf
import androidx.compose.memo
import androidx.compose.trace
import androidx.ui.core.ContextAmbient
import androidx.ui.graphics.Image
import androidx.ui.graphics.imageFromResource

/**
 * Synchronously load an image resource.
 *
 * Note: This API is transient and will be likely removed for encouraging async resource loading.
 *
 * @param id the resource identifier
 * @return the decoded image data associated with the resource
 */
@CheckResult(suggest = "+")
fun imageResource(@DrawableRes id: Int) = effectOf<Image> {
    val context = +ambient(ContextAmbient)
    val value = +memo { TypedValue() }
    context.resources.getValue(id, value, true)
    // We use the file path as a key of the request cache.
    // TODO(nona): Add density to the key?
    val key = value.string?.toString()
    +memo(key) { imageFromResource(context.resources, id) }
}

/**
 * Load the image in background thread.
 *
 * Until resource loading complete, this function returns deferred image resource with
 * [PendingResource]. Once the loading finishes, recompose is scheduled and this function will
 * return deferred image resource with [LoadedResource] or [FailedResource].
 *
 * @param id the resource identifier
 * @param pendingImage an optional image to be used during loading instead.
 * @param failedImage an optional image to be used if image loading failed.
 * @return the deferred image resource.
 */
@CheckResult(suggest = "+")
fun loadImageResource(
    id: Int,
    pendingImage: Image? = null,
    failedImage: Image? = null
) = effectOf<DeferredResource<Image>> {
    val context = +ambient(ContextAmbient)
    val res = context.resources
    +loadResource(id, pendingImage, failedImage) {
        trace("Image Resource Loading") {
            imageFromResource(res, id)
        }
    }
}