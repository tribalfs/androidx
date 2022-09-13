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
@file:JvmName("ViewTreeFullyLoadedReporterOwner")

package androidx.activity

import android.view.View

/**
 * Set the [FullyLoadedReporterOwner] associated with the given [View].
 * Calls to [findViewTreeFullyLoadedReporterOwner] from this [View] or descendants will
 * return [fullyLoadedReporterOwner].
 *
 * This should only be called by constructs such as activities that manage
 * a view tree and handle the dispatch of [ComponentActivity.reportFullyDrawn].
 *
 * @param fullyLoadedReporterOwner [FullyLoadedReporterOwner] associated with the [View]
 */
@JvmName("set")
fun View.setViewTreeFullyLoadedReporterOwner(
    fullyLoadedReporterOwner: FullyLoadedReporterOwner
) {
    setTag(R.id.report_loaded, fullyLoadedReporterOwner)
}

/**
 * Retrieve the [FullyLoadedReporterOwner] associated with the given [View].
 * This may be used to indicate that a part of the UI is loaded and ready for first
 * user interaction.
 *
 * @return The [FullyLoadedReporterOwner] associated with this view and/or some subset
 * of its ancestors
 */
@JvmName("get")
fun View.findViewTreeFullyLoadedReporterOwner(): FullyLoadedReporterOwner? {
    return generateSequence(this) {
        it.parent as? View
    }.mapNotNull {
        it.getTag(R.id.report_loaded) as? FullyLoadedReporterOwner
    }.firstOrNull()
}
