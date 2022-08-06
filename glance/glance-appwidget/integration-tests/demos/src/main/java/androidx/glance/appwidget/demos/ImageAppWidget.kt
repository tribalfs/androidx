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

package androidx.glance.appwidget.demos

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size

/**
 * Sample AppWidget that showcase the [ContentScale] options for [Image]
 */
class ImageAppWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        internal val ImageTypeKey = stringPreferencesKey("imageType")
    }

    @Composable
    override fun Content() {
        val type = currentState(ImageTypeKey) ?: "Fit"
        Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
            Button(
                text = "Content Scale: $type",
                modifier = GlanceModifier.fillMaxWidth(),
                onClick = actionRunCallback<ChangeImageAction>()
            )
            Spacer(GlanceModifier.size(4.dp))
            Image(
                provider = ImageProvider(R.drawable.compose),
                contentDescription = "Content Scale image sample (value: $type)",
                contentScale = type.toContentScale(),
                modifier = GlanceModifier.fillMaxSize().background(Color.DarkGray)
            )
        }
    }

    private fun String.toContentScale() = when (this) {
        "Fit" -> ContentScale.Fit
        "Fill Bounds" -> ContentScale.FillBounds
        "Crop" -> ContentScale.Crop
        else -> throw IllegalArgumentException()
    }
}

class ChangeImageAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { state ->
            val value = when (state[ImageAppWidget.ImageTypeKey]) {
                "Crop" -> "Fill Bounds"
                "Fill Bounds" -> "Fit"
                else -> "Crop"
            }
            state[ImageAppWidget.ImageTypeKey] = value
        }
        ImageAppWidget().update(context, glanceId)
    }
}

class ImageAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ImageAppWidget()
}