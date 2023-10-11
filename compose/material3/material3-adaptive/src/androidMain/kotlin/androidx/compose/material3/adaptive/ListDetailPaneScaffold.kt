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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMap

/**
 * A Material opinionated implementation of [ThreePaneScaffold] that will display the provided three
 * panes in a canonical list-detail layout.
 *
 * @param scaffoldState the state of the scaffold, which will decide the current layout directive
 *        and scaffold layout value, and perform navigation within the scaffold.
 * @param listPane the list pane of the scaffold. See [ListDetailPaneScaffoldRole.List].
 * @param modifier [Modifier] of the scaffold layout.
 * @param extraPane the list pane of the scaffold. See [ListDetailPaneScaffoldRole.Extra].
 * @param detailPane the list pane of the scaffold. See [ListDetailPaneScaffoldRole.Detail].
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
fun ListDetailPaneScaffold(
    listPane: @Composable ThreePaneScaffoldScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: ListDetailPaneScaffoldState = rememberListDetailPaneScaffoldState(),
    extraPane: (@Composable ThreePaneScaffoldScope.() -> Unit)? = null,
    detailPane: @Composable ThreePaneScaffoldScope.() -> Unit
) {
    ThreePaneScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldDirective = scaffoldState.scaffoldDirective,
        scaffoldValue = scaffoldState.scaffoldValue,
        arrangement = ThreePaneScaffoldDefaults.ListDetailLayoutArrangement,
        secondaryPane = listPane,
        tertiaryPane = extraPane,
        primaryPane = detailPane
    )
}

/**
 * Provides default values of [ListDetailPaneScaffold].
 */
@ExperimentalMaterial3AdaptiveApi
object ListDetailPaneScaffoldDefaults {
    /**
     * Creates a default [ThreePaneScaffoldAdaptStrategies] for [ListDetailPaneScaffold].
     *
     * @param detailPaneAdaptStrategy the adapt strategy of the primary pane
     * @param listPaneAdaptStrategy the adapt strategy of the secondary pane
     * @param extraPaneAdaptStrategy the adapt strategy of the tertiary pane
     */
    fun adaptStrategies(
        detailPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
        listPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
        extraPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
    ): ThreePaneScaffoldAdaptStrategies =
        ThreePaneScaffoldAdaptStrategies(
            detailPaneAdaptStrategy,
            listPaneAdaptStrategy,
            extraPaneAdaptStrategy
        )
}

/**
 * The state of [ListDetailPaneScaffold]. It provides the layout directive and value state that will
 * be updated directly. It also provides functions to perform navigation.
 *
 * Use [rememberListDetailPaneScaffoldState] to get a remembered default instance of this interface,
 * which works independently from any navigation frameworks. Developers can also integrate with
 * other navigation frameworks by implementing this interface.
 *
 * @property scaffoldDirective the current layout directives that the associated
 *           [ListDetailPaneScaffold] needs to follow. It's supposed to be automatically updated
 *           when the window configuration changes.
 * @property scaffoldValue the current layout value of the associated [ListDetailPaneScaffold], which
 *           represents unique layout states of the scaffold.
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
interface ListDetailPaneScaffoldState {
    val scaffoldDirective: PaneScaffoldDirective
    val scaffoldValue: ThreePaneScaffoldValue

    /**
     * Navigates to a new focus.
     */
    fun navigateTo(pane: ListDetailPaneScaffoldRole)

    /**
     * Returns `true` if there is a previous focus to navigate back to.
     *
     * @param scaffoldValueMustChange `true` if the navigation operation should only be performed when
     *        there are actual layout value changes.
     */
    fun canNavigateBack(scaffoldValueMustChange: Boolean = true): Boolean

    /**
     * Navigates to the previous focus.
     *
     * @param popUntilScaffoldValueChange `true` if the backstack should be popped until the layout
     *        value changes.
     */
    fun navigateBack(popUntilScaffoldValueChange: Boolean = true): Boolean
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private class DefaultListDetailPaneScaffoldState(
    val internalState: DefaultThreePaneScaffoldState
) : ListDetailPaneScaffoldState {
    override val scaffoldDirective get() = internalState.scaffoldDirective
    override val scaffoldValue get() = internalState.scaffoldValue

    override fun navigateTo(pane: ListDetailPaneScaffoldRole) {
        internalState.navigateTo(pane.threePaneScaffoldRole)
    }

    override fun canNavigateBack(scaffoldValueMustChange: Boolean): Boolean =
        internalState.canNavigateBack(scaffoldValueMustChange)

    override fun navigateBack(popUntilScaffoldValueChange: Boolean): Boolean =
        internalState.navigateBack(popUntilScaffoldValueChange)
}

/**
 * Returns a remembered default implementation of [ListDetailPaneScaffoldState], which will
 * be updated automatically when the input values change. The default state is supposed to be
 * used independently from any navigation frameworks and it will address the navigation purely
 * inside the [ListDetailPaneScaffold].
 *
 * @param scaffoldDirective the current layout directives to follow. The default value will be
 *        Calculated with [calculateStandardPaneScaffoldDirective] using [WindowAdaptiveInfo]
 *        retrieved from the current context.
 * @param adaptStrategies adaptation strategies of each pane.
 * @param initialFocusHistory the initial focus history of the scaffold, by default it will be just
 *        the list pane.
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
fun rememberListDetailPaneScaffoldState(
    scaffoldDirective: PaneScaffoldDirective =
        calculateStandardPaneScaffoldDirective(calculateWindowAdaptiveInfo()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    initialFocusHistory: List<ListDetailPaneScaffoldRole> = listOf(ListDetailPaneScaffoldRole.List)
): ListDetailPaneScaffoldState {
    val internalState = rememberDefaultThreePaneScaffoldState(
        scaffoldDirective,
        adaptStrategies,
        initialFocusHistory.fastMap { it.threePaneScaffoldRole }
    )
    return remember(internalState) {
        DefaultListDetailPaneScaffoldState(internalState)
    }
}

/**
 * The set of the available pane roles of [ListDetailPaneScaffold].
 */
@ExperimentalMaterial3AdaptiveApi
enum class ListDetailPaneScaffoldRole(internal val threePaneScaffoldRole: ThreePaneScaffoldRole) {
    /**
     * The list pane of [ListDetailPaneScaffold]. It is mapped to [ThreePaneScaffoldRole.Secondary].
     */
    List(ThreePaneScaffoldRole.Secondary),

    /**
     * The detail pane of [ListDetailPaneScaffold]. It is mapped to [ThreePaneScaffoldRole.Primary].
     */
    Detail(ThreePaneScaffoldRole.Primary),

    /**
     * The extra pane of [ListDetailPaneScaffold]. It is mapped to [ThreePaneScaffoldRole.Tertiary].
     */
    Extra(ThreePaneScaffoldRole.Tertiary);
}
