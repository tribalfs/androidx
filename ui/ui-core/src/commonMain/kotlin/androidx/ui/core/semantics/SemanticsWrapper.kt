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

package androidx.ui.core.semantics

import androidx.ui.core.DelegatingLayoutNodeWrapper
import androidx.ui.core.ExperimentalLayoutNodeApi
import androidx.ui.core.LayoutNodeWrapper

@OptIn(ExperimentalLayoutNodeApi::class)
internal class SemanticsWrapper(
    wrapped: LayoutNodeWrapper,
    val semanticsModifier: SemanticsModifier
) : DelegatingLayoutNodeWrapper<SemanticsModifier>(wrapped, semanticsModifier) {
    fun collapsedSemanticsConfiguration(): SemanticsConfiguration {
        var config = SemanticsConfiguration()
        config.absorb(modifier.semanticsConfiguration, ignoreAlreadySet = true)

        val innerConfig = wrapped.nearestSemantics?.collapsedSemanticsConfiguration()
        if (innerConfig != null) {
            config.absorb(innerConfig, ignoreAlreadySet = true)
        }
        return config
    }

    override fun detach() {
        super.detach()
        layoutNode.owner?.onSemanticsChange()
    }

    override fun onModifierChanged() {
        layoutNode.owner?.onSemanticsChange()
    }

    override fun toString(): String {
        return "${super.toString()} localConfig: ${modifier.semanticsConfiguration}"
    }
}
