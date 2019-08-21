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

import androidx.ui.core.LayoutNode
import androidx.ui.core.SemanticsComponentNode
import androidx.ui.core.semantics.SemanticsConfiguration
import androidx.ui.core.semantics.SemanticsNode

private var stubId = 2

fun SemanticsTreeNodeStub(data: SemanticsConfiguration): SemanticsNode {
    // TODO: Find a better way to stub this stuff, this just bridges the old SemanticsTreeNode
    //  system to use SemanticsNodes
    val stubSemanticsComponentNode = SemanticsComponentNode(data, stubId++)
    val stubLayoutNode = LayoutNode()
    stubSemanticsComponentNode.emitInsertAt(0, stubLayoutNode)
    return SemanticsNode(data, stubSemanticsComponentNode)
}
