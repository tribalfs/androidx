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

import androidx.ui.core.AndroidOwner
import androidx.ui.core.semantics.SemanticsNode
import androidx.ui.test.android.AndroidInputDispatcher

/**
 * Represents a semantics node and the path to fetch it from the semantics tree. One can interact
 * with this node by performing actions such as [doClick], assertions such as
 * [assertHasClickAction], or navigate to other nodes such as [children].
 *
 * This is usually obtained from methods like [findByTag], [find].
 *
 * Example usage:
 * ```
 * findByTag("myCheckbox")
 *    .doClick()
 *    .assertIsOn()
 * ````
 */
class SemanticsNodeInteraction internal constructor(
    internal val selector: SemanticsSelector
) {
    private var nodeIds: List<Int>? = null

    /**
     * Anytime we refresh semantics we capture it here. This is then presented to the user in case
     * their tests fails deu to a missing node. This helps to see what was the last state of the
     * node before it disappeared. We dump it to string because trying to dump the node later can
     * result in failure as it gets detached from its layout.
     */
    private var lastSeenSemantics: String? = null

    internal fun fetchSemanticsNodes(errorMessageOnFail: String? = null): SelectionResult {
        if (nodeIds == null) {
            return selector
                .map(getAllSemanticsNodes(), errorMessageOnFail.orEmpty())
                .apply { nodeIds = selectedNodes.map { it.id }.toList() }
        }

        return SelectionResult(getAllSemanticsNodes().filter { it.id in nodeIds!! })
    }

    /**
     * Returns the semantics node captured by this object.
     *
     * Note: Accessing this object involves synchronization with your UI. If you are accessing this
     * multiple times in one atomic operation, it is better to cache the result instead of calling
     * this API multiple times.
     *
     * This will fail if there is 0 or multiple nodes matching.
     *
     * @throws AssertionError if 0 or multiple nodes found.
     */
    fun fetchSemanticsNode(errorMessageOnFail: String? = null): SemanticsNode {
        return fetchOneOrDie(errorMessageOnFail)
    }

    /**
     * Asserts that no item was found or that the item is no longer in the hierarchy.
     *
     * This will synchronize with the UI and fetch all the nodes again to ensure it has latest data.
     *
     * @throws [AssertionError] if the assert fails.
     */
    fun assertDoesNotExist() {
        val result = fetchSemanticsNodes("Failed: assertDoesNotExist.")
        if (result.selectedNodes.isNotEmpty()) {
            throw AssertionError(buildErrorMessageForCountMismatch(
                errorMessage = "Failed: assertDoesNotExist.",
                selector = selector,
                foundNodes = result.selectedNodes,
                expectedCount = 0
            ))
        }
    }

    /**
     * Asserts that the component was found and is part of the component tree.
     *
     * This will synchronize with the UI and fetch all the nodes again to ensure it has latest data.
     * If you are using [fetchSemanticsNode] you don't need to call this. In fact you would just
     * introduce additional overhead.
     *
     * @param errorMessageOnFail Error message prefix to be added to the message in case this
     * asserts fails. This is typically used by operations that rely on this assert. Example prefix
     * could be: "Failed to perform doOnClick.".
     *
     * @throws [AssertionError] if the assert fails.
     */
    fun assertExists(errorMessageOnFail: String? = null): SemanticsNodeInteraction {
        fetchOneOrDie(errorMessageOnFail)
        return this
    }

    private fun fetchOneOrDie(errorMessageOnFail: String? = null): SemanticsNode {
        val finalErrorMessage = errorMessageOnFail
            ?: "Failed: assertExists."

        val result = fetchSemanticsNodes(finalErrorMessage)
        if (result.selectedNodes.count() != 1) {
            if (result.selectedNodes.isEmpty() && lastSeenSemantics != null) {
                // This means that node we used to have is no longer in the tree.
                throw AssertionError(buildErrorMessageForNodeMissingInTree(
                    errorMessage = finalErrorMessage,
                    selector = selector,
                    lastSeenSemantics = lastSeenSemantics!!
                ))
            }

            if (result.customErrorOnNoMatch != null) {
                throw AssertionError(finalErrorMessage + "\n" + result.customErrorOnNoMatch)
            }

            throw AssertionError(buildErrorMessageForCountMismatch(
                errorMessage = finalErrorMessage,
                foundNodes = result.selectedNodes,
                expectedCount = 1,
                selector = selector
            ))
        }

        lastSeenSemantics = result.selectedNodes.first().toStringInfo()
        return result.selectedNodes.first()
    }
}

/**
 * Represents a collection of semantics nodes and the path to fetch them from the semantics tree.
 * One can interact with these nodes by performing assertions such as [assertCountEquals], or
 * navigate to other nodes such as [get].
 *
 * This is usually obtained from methods like [findAll] or chains of [find].[children].
 *
 * Example usage:
 * ```
 * findAll(isClickable())
 *    .assertCountEquals(2)
 * ````
 */
class SemanticsNodeInteractionCollection(
    internal val selector: SemanticsSelector
) {
    private var nodeIds: List<Int>? = null

    /**
     * Returns the semantics nodes captured by this object.
     *
     * Note: Accessing this object involves synchronization with your UI. If you are accessing this
     * multiple times in one atomic operation, it is better to cache the result instead of calling
     * this API multiple times.
     */
    fun fetchSemanticsNodes(errorMessageOnFail: String? = null): List<SemanticsNode> {
        if (nodeIds == null) {
            return selector
                .map(getAllSemanticsNodes(), errorMessageOnFail.orEmpty())
                .apply { nodeIds = selectedNodes.map { it.id }.toList() }
                .selectedNodes
        }

        return getAllSemanticsNodes().filter { it.id in nodeIds!! }
    }

    /**
     * Retrieve node at the given index of this collection.
     *
     * Any subsequent operation on its result will expect exactly one element found (unless
     * [SemanticsNodeInteraction.assertDoesNotExist] is used) and will throw [AssertionError] if
     * none or more than one element is found.
     */
    operator fun get(index: Int): SemanticsNodeInteraction {
        return SemanticsNodeInteraction(selector.addIndexSelector(index))
    }
}

private var inputDispatcherFactory: (SemanticsNode) -> InputDispatcher = { node ->
    val view = (node.componentNode.owner as AndroidOwner).view
    AndroidInputDispatcher { view.dispatchTouchEvent(it) }
}
