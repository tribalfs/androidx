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

package androidx.compose.ui.inspection.compose

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.R
import androidx.compose.ui.inspection.framework.ancestors
import androidx.compose.ui.inspection.framework.getChildren
import androidx.compose.ui.inspection.framework.isRoot
import androidx.compose.ui.inspection.inspector.InspectorNode
import androidx.compose.ui.inspection.inspector.LayoutInspectorTree
import androidx.compose.ui.inspection.util.ThreadUtils

/**
 * Returns true if this view represents a special type that bridges between the legacy UI
 * framework and Jetpack Compose.
 *
 * Note: AndroidComposeView lives in compose.ui but is internal, which is why we need to check
 * indirectly like this. TODO(b/177998085): Expose this class to our library.
 */
private fun View.isAndroidComposeView(): Boolean {
    return javaClass.canonicalName == "androidx.compose.ui.platform.AndroidComposeView"
}

/**
 * Returns true if the view is contained in a layout generated by the platform / system.
 *
 * <ul>
 *   <li>DecorView, ViewStub, AndroidComposeView, View will have a null layout
 *   <li>Layouts from the "android" namespace are from the platform
 *   <li>AppCompat will typically use an "abc_" prefix for their layout names
 * </ul>
 */
private fun View.isSystemView(): Boolean {
    return try {
        val layoutId = sourceLayoutResId
        if (layoutId == 0) {
            // Programmatically added Views are treated as system views:
            return true
        }
        val namespace = resources.getResourcePackageName(layoutId)
        val name = resources.getResourceEntryName(layoutId)

        namespace == "android" || name.startsWith("abc_")
    } catch (ignored: Resources.NotFoundException) {
        false
    }
}

/**
 * The `AndroidComposeView` class inside the compose library is internal, so we make our own fake
 * class there that wraps a normal [View], verifies it's the expected type, and exposes compose
 * related data that we care about.
 *
 * As this class extracts information about the view it's targeting, it must be instantiated on the
 * UI thread.
 */
class AndroidComposeViewWrapper(
    private val layoutInspectorTree: LayoutInspectorTree,
    val rootView: View,
    private val composeView: ViewGroup,
    skipSystemComposables: Boolean
) {
    companion object {
        fun tryCreateFor(
            layoutInspectorTree: LayoutInspectorTree,
            rootView: View,
            composeView: View,
            skipSystemComposables: Boolean
        ): AndroidComposeViewWrapper? {
            return if (composeView.isAndroidComposeView()) {
                AndroidComposeViewWrapper(
                    layoutInspectorTree,
                    rootView,
                    composeView as ViewGroup,
                    skipSystemComposables
                )
            } else {
                null
            }
        }
    }

    init {
        ThreadUtils.assertOnMainThread()
        check(composeView.isAndroidComposeView())
    }

    val viewParent =
        if (!skipSystemComposables) composeView
        else composeView.ancestors().first { !it.isSystemView() || it.isRoot() }

    val viewsToSkip: List<Long> =
        composeView.getChildren()
            .filter { it.getTag(R.id.hide_in_inspector_tag) != null }
            .map { it.uniqueDrawingId }

    private val inspectorNodes = layoutInspectorTree.apply {
        this.hideSystemNodes = skipSystemComposables
    }.convert(composeView)

    fun createNodes(): List<InspectorNode> =
        layoutInspectorTree.addSubCompositionRoots(composeView, inspectorNodes)
}
