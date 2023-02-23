/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.window.embedding

import android.app.Activity
import android.content.Context

/** The controller that allows checking the current [Activity] embedding status. */
class ActivityEmbeddingController internal constructor(private val backend: EmbeddingBackend) {
    /**
     * Checks if the [activity] is embedded and its presentation may be customized by the host
     * process of the task this [activity] is associated with.
     *
     * @param activity the [Activity] to check.
     */
    // TODO(b/204399167) Migrate to a Flow
    fun isActivityEmbedded(activity: Activity): Boolean =
        backend.isActivityEmbedded(activity)

    companion object {
        /**
         * Obtains an instance of [ActivityEmbeddingController].
         *
         * @param context the [Context] to initialize the controller with
         */
        @JvmStatic
        fun getInstance(context: Context): ActivityEmbeddingController {
            val backend = EmbeddingBackend.getInstance(context)
            return ActivityEmbeddingController(backend)
        }
    }
}