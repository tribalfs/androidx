/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.material3.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection

/**
 * Represents the horizontal order of panes in a [ThreePaneScaffold] from start to end. Note that
 * the values of [firstPane], [secondPane] and [thirdPane] have to be different, otherwise
 * [IllegalArgumentException] will be thrown.
 *
 * @constructor create an instance of [ThreePaneScaffoldHorizontalOrder]
 * @param firstPane The first pane from the start of the [ThreePaneScaffold]
 * @param secondPane The second pane from the start of the [ThreePaneScaffold]
 * @param thirdPane The third pane from the start of the [ThreePaneScaffold]
 */
@ExperimentalMaterial3AdaptiveApi
@Immutable
internal class ThreePaneScaffoldHorizontalOrder(
    val firstPane: ThreePaneScaffoldRoleInternal,
    val secondPane: ThreePaneScaffoldRoleInternal,
    val thirdPane: ThreePaneScaffoldRoleInternal
) {
    init {
        require(firstPane != secondPane && secondPane != thirdPane && firstPane != thirdPane) {
            "invalid ThreePaneScaffoldHorizontalOrder($firstPane, $secondPane, $thirdPane)" +
                " - panes must be unique"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ThreePaneScaffoldHorizontalOrder) return false
        if (firstPane != other.firstPane) return false
        if (secondPane != other.secondPane) return false
        if (thirdPane != other.thirdPane) return false
        return true
    }

    override fun hashCode(): Int {
        var result = firstPane.hashCode()
        result = 31 * result + secondPane.hashCode()
        result = 31 * result + thirdPane.hashCode()
        return result
    }
}

/**
 * Converts a bidirectional order to a left-to-right order.
 */
@ExperimentalMaterial3AdaptiveApi
internal fun ThreePaneScaffoldHorizontalOrder.toLtrOrder(
    layoutDirection: LayoutDirection
): ThreePaneScaffoldHorizontalOrder {
    return if (layoutDirection == LayoutDirection.Rtl) {
        ThreePaneScaffoldHorizontalOrder(
            thirdPane,
            secondPane,
            firstPane
        )
    } else {
        this
    }
}

@ExperimentalMaterial3AdaptiveApi
internal inline fun ThreePaneScaffoldHorizontalOrder.forEach(
    action: (ThreePaneScaffoldRoleInternal) -> Unit
) {
    action(firstPane)
    action(secondPane)
    action(thirdPane)
}

@ExperimentalMaterial3AdaptiveApi
internal inline fun ThreePaneScaffoldHorizontalOrder.forEachIndexed(
    action: (Int, ThreePaneScaffoldRoleInternal) -> Unit
) {
    action(0, firstPane)
    action(1, secondPane)
    action(2, thirdPane)
}

@ExperimentalMaterial3AdaptiveApi
internal fun ThreePaneScaffoldHorizontalOrder.indexOf(role: ThreePaneScaffoldRoleInternal): Int {
    forEachIndexed { i, r ->
        if (r == role) {
            return i
        }
    }
    // should never reach this far
    return 0
}

/**
 * The set of the available pane roles of [ThreePaneScaffold].
 */
@ExperimentalMaterial3AdaptiveApi
internal enum class ThreePaneScaffoldRoleInternal {
    /**
     * The primary pane of [ThreePaneScaffold]. It is supposed to have the highest priority during
     * layout adaptation and usually contains the most important content of the screen, like content
     * details in a list-detail settings.
     */
    Primary,

    /**
     * The secondary pane of [ThreePaneScaffold]. It is supposed to have the second highest priority
     * during layout adaptation and usually contains the supplement content of the screen, like
     * content list in a list-detail settings.
     */
    Secondary,

    /**
     * The tertiary pane of [ThreePaneScaffold]. It is supposed to have the lowest priority during
     * layout adaptation and usually contains the additional info which will only be shown under
     * user interaction.
     */
    Tertiary
}

/**
 * The base class to represent the set of the available pane roles of a specific three pane scaffold
 * implementation.
 *
 * @see [ListDetailPaneScaffoldRole]
 * @see [SupportingPaneScaffoldRole]
 */
@ExperimentalMaterial3AdaptiveApi
abstract class ThreePaneScaffoldRole internal constructor(
    internal val internalRole: ThreePaneScaffoldRoleInternal
)
