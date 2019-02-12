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

package androidx.ui.test

import android.view.MotionEvent
import androidx.ui.core.SemanticsTreeNode

class SemanticsTreeQuery internal constructor(
    private val uiTestRunner: UiTestRunner,
    private val selector: (SemanticsTreeNode) -> Boolean
) {

    private var cachedNodes: List<SemanticsTreeNode>? = null

    internal fun findAllMatching(): List<SemanticsTreeNode> {
        if (cachedNodes == null) {
            cachedNodes = uiTestRunner.findSemantics(selector)
        }
        return cachedNodes!!
    }

    internal fun sendEvent(event: MotionEvent) {
        uiTestRunner.sendEvent(event)
    }
}