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

import android.annotation.SuppressLint
import android.content.res.Resources
import android.util.Xml
import androidx.annotation.CheckResult
import androidx.annotation.DrawableRes
import androidx.compose.ambient
import androidx.compose.effectOf
import androidx.compose.memo
import androidx.compose.trace
import androidx.ui.core.ContextAmbient
import androidx.ui.graphics.vector.VectorAsset
import androidx.ui.graphics.vector.compat.createVectorImageBuilder
import androidx.ui.graphics.vector.compat.isAtEnd
import androidx.ui.graphics.vector.compat.parseCurrentVectorNode
import androidx.ui.graphics.vector.compat.seekToStartTag
import org.xmlpull.v1.XmlPullParserException

/**
 * Effect used to load a [VectorAsset] from an Android resource id
 * This is useful for querying top level properties of the [VectorAsset]
 * such as it's intrinsic width and height to be able to size components
 * based off of it's dimensions appropriately
 *
 * Note: This API is transient and will be likely removed for encouraging async resource loading.
 */
@CheckResult(suggest = "+")
fun vectorResource(@DrawableRes id: Int) = effectOf<VectorAsset> {
    val context = +ambient(ContextAmbient)
    val res = context.resources
    val theme = context.theme
    +memo(id) {
        loadVectorResource(theme, res, id)
    }
}

/**
 * Load the vector drawable in background thread.
 *
 * Until resource loading complete, this function returns deferred vector drawable resource with
 * [PendingResource]. Once the loading finishes, recompose is scheduled and this function will
 * return deferred vector drawable resource with [LoadedResource] or [FailedResource].
 *
 * @param id the resource identifier
 * @param pendingResource an optional resource to be used during loading instead.
 * @param failedResource an optional resource to be used if resource loading failed.
 * @return the deferred vector drawable resource.
 */
@CheckResult(suggest = "+")
fun loadVectorResource(
    id: Int,
    pendingResource: VectorAsset? = null,
    failedResource: VectorAsset? = null
) = effectOf<DeferredResource<VectorAsset>> {
    val context = +ambient(ContextAmbient)
    val res = context.resources
    val theme = context.theme

    +loadResource(id, pendingResource, failedResource) {
        trace("Vector Resource Loading") {
            loadVectorResource(theme, res, id)
        }
    }
}

@Throws(XmlPullParserException::class)
@SuppressWarnings("RestrictedApi")
internal fun loadVectorResource(
    theme: Resources.Theme? = null,
    res: Resources,
    resId: Int
): VectorAsset {
    @SuppressLint("ResourceType") val parser = res.getXml(resId)
    val attrs = Xml.asAttributeSet(parser)
    val builder = parser.seekToStartTag().createVectorImageBuilder(res, theme, attrs)

    while (!parser.isAtEnd()) {
        parser.parseCurrentVectorNode(res, attrs, theme, builder)
        parser.next()
    }
    return builder.build()
}