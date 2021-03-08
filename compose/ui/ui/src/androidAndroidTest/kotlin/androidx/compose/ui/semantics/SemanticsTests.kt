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

package androidx.compose.ui.semantics

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import kotlin.math.max

@MediumTest
@RunWith(AndroidJUnit4::class)
class SemanticsTests {
    private val TestTag = "semantics-test-tag"

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    private fun executeUpdateBlocking(updateFunction: () -> Unit) {
        val latch = CountDownLatch(1)
        rule.runOnUiThread {
            updateFunction()
            latch.countDown()
        }

        latch.await()
    }

    @Test
    fun unchangedSemanticsDoesNotCauseRelayout() {
        val layoutCounter = Counter(0)
        val recomposeForcer = mutableStateOf(0)
        rule.setContent {
            recomposeForcer.value
            CountingLayout(Modifier.semantics { contentDescription = "label" }, layoutCounter)
        }

        rule.runOnIdle { assertEquals(1, layoutCounter.count) }

        rule.runOnIdle { recomposeForcer.value++ }

        rule.runOnIdle { assertEquals(1, layoutCounter.count) }
    }

    @Test
    fun depthFirstLabelConcat() {
        val root = "root"
        val child1 = "child1"
        val grandchild1 = "grandchild1"
        val grandchild2 = "grandchild2"
        val child2 = "grandchild2"
        rule.setContent {
            SimpleTestLayout(
                Modifier.testTag(TestTag)
                    .semantics(mergeDescendants = true) { contentDescription = root }
            ) {
                SimpleTestLayout(Modifier.semantics { contentDescription = child1 }) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = grandchild1 }) { }
                    SimpleTestLayout(Modifier.semantics { contentDescription = grandchild2 }) { }
                }
                SimpleTestLayout(Modifier.semantics { contentDescription = child2 }) { }
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(
            "$root, $child1, $grandchild1, $grandchild2, $child2"
        )
    }

    @Test
    fun nestedMergedSubtree() {
        val tag1 = "tag1"
        val tag2 = "tag2"
        val label1 = "foo"
        val label2 = "bar"
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag1)) {
                SimpleTestLayout(Modifier.semantics { contentDescription = label1 }) { }
                SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag2)) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label2 }) { }
                }
            }
        }

        rule.onNodeWithTag(tag1).assertContentDescriptionEquals(label1)
        rule.onNodeWithTag(tag2).assertContentDescriptionEquals(label2)
    }

    @Test
    fun nestedMergedSubtree_includeAllMergeableChildren() {
        val tag1 = "tag1"
        val tag2 = "tag2"
        val label1 = "foo"
        val label2 = "bar"
        val label3 = "hi"
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag1)) {
                SimpleTestLayout(Modifier.semantics { contentDescription = label1 }) { }
                SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag2)) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label2 }) { }
                }
                SimpleTestLayout(Modifier.semantics { contentDescription = label3 }) { }
            }
        }

        rule.onNodeWithTag(tag1).assertContentDescriptionEquals("$label1, $label3")
        rule.onNodeWithTag(tag2).assertContentDescriptionEquals(label2)
    }

    @Test
    fun clearAndSetSemantics() {
        val tag1 = "tag1"
        val tag2 = "tag2"
        val label1 = "foo"
        val label2 = "hidden"
        val label3 = "baz"
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag1)) {
                SimpleTestLayout(Modifier.semantics { contentDescription = label1 }) { }
                SimpleTestLayout(Modifier.clearAndSetSemantics {}) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label2 }) { }
                }
                SimpleTestLayout(Modifier.clearAndSetSemantics { contentDescription = label3 }) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label2 }) { }
                }
                SimpleTestLayout(
                    Modifier.semantics(mergeDescendants = true) {}.testTag(tag2)
                        .clearAndSetSemantics { text = AnnotatedString(label1) }
                ) {
                    SimpleTestLayout(Modifier.semantics { text = AnnotatedString(label2) }) { }
                }
            }
        }

        rule.onNodeWithTag(tag1).assertContentDescriptionEquals("$label1, $label3")
        rule.onNodeWithTag(tag2).assertTextEquals(label1)
    }

    @Test
    fun clearAndSetSemanticsSameLayoutNode() {
        val tag1 = "tag1"
        val tag2 = "tag2"
        val label1 = "foo"
        val label2 = "hidden"
        val label3 = "baz"
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(tag1)) {
                SimpleTestLayout(
                    Modifier
                        .clearAndSetSemantics { contentDescription = label1 }
                        .semantics { text = AnnotatedString(label2) }
                ) {}
                SimpleTestLayout(
                    Modifier
                        .semantics { contentDescription = label3 }
                        .clearAndSetSemantics { text = AnnotatedString(label3) }
                ) {}
            }
            SimpleTestLayout(
                Modifier.testTag(tag2)
                    .semantics { contentDescription = label1 }
                    .clearAndSetSemantics {}
                    .semantics { text = AnnotatedString(label1) }
            ) {}
        }

        rule.onNodeWithTag(tag1).assertContentDescriptionEquals("$label1, $label3")
        rule.onNodeWithTag(tag1).assertTextEquals(label3)
        rule.onNodeWithTag(tag2).assertContentDescriptionEquals("$label1")
        rule.onNodeWithTag(tag2).assertDoesNotHaveProperty(SemanticsProperties.Text)
    }

    @Test
    fun removingMergedSubtree_updatesSemantics() {
        val label = "foo"
        val showSubtree = mutableStateOf(true)
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(TestTag)) {
                if (showSubtree.value) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label }) { }
                }
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(label)

        rule.runOnIdle { showSubtree.value = false }

        rule.onNodeWithTag(TestTag)
            .assertDoesNotHaveProperty(SemanticsProperties.ContentDescription)

        rule.onAllNodesWithText(label).assertCountEquals(0)
    }

    @Test
    fun addingNewMergedNode_updatesSemantics() {
        val label = "foo"
        val value = "bar"
        val showNewNode = mutableStateOf(false)
        rule.setContent {
            SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}.testTag(TestTag)) {
                SimpleTestLayout(Modifier.semantics { contentDescription = label }) { }
                if (showNewNode.value) {
                    SimpleTestLayout(Modifier.semantics { stateDescription = value }) { }
                }
            }
        }

        rule.onNodeWithTag(TestTag)
            .assertContentDescriptionEquals(label)
            .assertDoesNotHaveProperty(SemanticsProperties.StateDescription)

        rule.runOnIdle { showNewNode.value = true }

        rule.onNodeWithTag(TestTag)
            .assertContentDescriptionEquals(label)
            .assertValueEquals(value)
    }

    @Test
    fun removingSubtreeWithoutSemanticsAsTopNode_updatesSemantics() {
        val label = "foo"
        val showSubtree = mutableStateOf(true)
        rule.setContent {
            SimpleTestLayout(Modifier.testTag(TestTag)) {
                if (showSubtree.value) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label }) { }
                }
            }
        }

        rule.onAllNodesWithContentDescription(label).assertCountEquals(1)

        rule.runOnIdle {
            showSubtree.value = false
        }

        rule.onAllNodesWithContentDescription(label).assertCountEquals(0)
    }

    @Test
    fun changingStackedSemanticsComponent_updatesSemantics() {
        val beforeLabel = "before"
        val afterLabel = "after"
        val isAfter = mutableStateOf(false)
        rule.setContent {
            SimpleTestLayout(
                Modifier.testTag(TestTag).semantics {
                    contentDescription = if (isAfter.value) afterLabel else beforeLabel
                }
            ) {}
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)

        rule.runOnIdle { isAfter.value = true }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
    }

    @Test
    fun changingStackedSemanticsComponent_notTopMost_updatesSemantics() {
        val beforeLabel = "before"
        val afterLabel = "after"
        val isAfter = mutableStateOf(false)

        rule.setContent {
            SimpleTestLayout(Modifier.testTag("don't care")) {
                SimpleTestLayout(
                    Modifier.testTag(TestTag).semantics {
                        contentDescription = if (isAfter.value) afterLabel else beforeLabel
                    }
                ) {}
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)

        rule.runOnIdle { isAfter.value = true }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
    }

    @Test
    fun changingSemantics_belowStackedLayoutNodes_updatesCorrectly() {
        val beforeLabel = "before"
        val afterLabel = "after"
        val isAfter = mutableStateOf(false)

        rule.setContent {
            SimpleTestLayout {
                SimpleTestLayout {
                    SimpleTestLayout(
                        Modifier.testTag(TestTag).semantics {
                            contentDescription = if (isAfter.value) afterLabel else beforeLabel
                        }
                    ) {}
                }
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)

        rule.runOnIdle { isAfter.value = true }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
    }

    @Test
    fun changingSemantics_belowNodeMergedThroughBoundary_updatesCorrectly() {
        val beforeLabel = "before"
        val afterLabel = "after"
        val isAfter = mutableStateOf(false)

        rule.setContent {
            SimpleTestLayout(Modifier.testTag(TestTag).semantics(mergeDescendants = true) {}) {
                SimpleTestLayout(
                    Modifier.semantics {
                        contentDescription = if (isAfter.value) afterLabel else beforeLabel
                    }
                ) {}
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)

        rule.runOnIdle { isAfter.value = true }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
    }

    @Test
    fun mergeDescendants_doesNotCrossLayoutNodesUpward() {
        val label = "label"
        rule.setContent {
            SimpleTestLayout(Modifier.testTag(TestTag)) {
                SimpleTestLayout(Modifier.semantics(mergeDescendants = true) {}) {
                    SimpleTestLayout(Modifier.semantics { contentDescription = label }) { }
                }
            }
        }

        rule.onNodeWithTag(TestTag)
            .assertDoesNotHaveProperty(SemanticsProperties.ContentDescription)
        rule.onNodeWithContentDescription(label) // assert exists
    }

    @Test
    fun updateToNodeWithMultipleBoundaryChildren_updatesCorrectly() {
        // This test reproduced a bug that caused a ConcurrentModificationException when
        // detaching SemanticsNodes

        val beforeLabel = "before"
        val afterLabel = "after"
        val isAfter = mutableStateOf(false)

        rule.setContent {
            SimpleTestLayout(
                Modifier.testTag(TestTag).semantics {
                    contentDescription = if (isAfter.value) afterLabel else beforeLabel
                }
            ) {
                SimpleTestLayout(Modifier.semantics { }) { }
                SimpleTestLayout(Modifier.semantics { }) { }
            }
        }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)

        rule.runOnIdle { isAfter.value = true }

        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
    }

    @Test
    fun changingSemantics_doesNotReplaceNodesBelow() {
        // Regression test for b/148606417
        var nodeCount = 0
        val beforeLabel = "before"
        val afterLabel = "after"

        // Do different things in an attempt to defeat a sufficiently clever compiler
        val beforeAction = { println("this never gets called") }
        val afterAction = { println("neither does this") }

        val isAfter = mutableStateOf(false)

        rule.setContent {
            SimpleTestLayout(
                Modifier.testTag(TestTag).semantics {
                    contentDescription = if (isAfter.value) afterLabel else beforeLabel
                    onClick(
                        action = {
                            if (isAfter.value) afterAction() else beforeAction()
                            return@onClick true
                        }
                    )
                }
            ) {
                SimpleTestLayout {
                    nodeCount++
                }
            }
        }

        // This isn't the important part, just makes sure everything is behaving as expected
        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(beforeLabel)
        assertThat(nodeCount).isEqualTo(1)

        rule.runOnIdle { isAfter.value = true }

        // Make sure everything is still behaving as expected
        rule.onNodeWithTag(TestTag).assertContentDescriptionEquals(afterLabel)
        // This is the important part: make sure we didn't replace the identity due to unwanted
        // pivotal properties
        assertThat(nodeCount).isEqualTo(1)
    }

    @Test
    fun collapseSemanticsActions_prioritizeNonNullAction() {
        val actionLabel = "copy"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics {
                        copyText(label = actionLabel, action = null)
                    }
                    .semantics {
                        copyText { true }
                    }
            ) {}
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("collapse copyText") {
                    it.config.getOrNull(SemanticsActions.CopyText)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.CopyText)?.action?.invoke() == true
                }
            )
    }

    @Test
    fun collapseSemanticsActions_prioritizeNonNullLabel() {
        val actionLabel = "copy"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics {
                        copyText { false }
                    }
                    .semantics {
                        copyText(label = actionLabel, action = { true })
                    }
            ) {}
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("collapse copyText") {
                    it.config.getOrNull(SemanticsActions.CopyText)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.CopyText)?.action?.invoke() == false
                }
            )
    }

    @Test
    fun collapseSemanticsActions_changeActionLabel_notMergeDescendants() {
        val actionLabel = "send"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics {
                        onClick(label = actionLabel, action = null)
                    }
                    .clickable {}
            ) {}
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("collapse onClick") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
                }
            )
    }

    @Test
    fun collapseSemanticsActions_changeActionLabel_mergeDescendants() {
        val actionLabel = "send"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics(mergeDescendants = true) {
                        onClick(label = actionLabel, action = null)
                    }
                    .clickable {}
            ) {}
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("collapse onClick") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
                }
            )
    }

    @Test
    fun mergeSemanticsActions_prioritizeNonNullAction_mergeDescendants_descendantMergeable() {
        val actionLabel = "show more"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics(mergeDescendants = true) {
                        expand(label = actionLabel, action = null)
                    }
            ) {
                SimpleTestLayout(Modifier.semantics { expand { true } }) {}
            }
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("merge expand action") {
                    it.config.getOrNull(SemanticsActions.Expand)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.Expand)?.action?.invoke() == true
                }
            )
    }

    @Test
    fun mergeSemanticsActions_prioritizeNonNullLabel_mergeDescendants_descendantMergeable() {
        val actionLabel = "show more"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics(mergeDescendants = true) {
                        expand { false }
                    }
            ) {
                SimpleTestLayout(
                    Modifier.semantics { expand(label = actionLabel, action = { true }) }
                ) {}
            }
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("merge expand action") {
                    it.config.getOrNull(SemanticsActions.Expand)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.Expand)?.action?.invoke() == false
                }
            )
    }

    @Test
    fun mergeSemanticsActions_changeActionLabelNotWork_notMergeDescendants_descendantMergeable() {
        val actionLabel = "show less"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics {
                        collapse(label = actionLabel, action = null)
                    }
            ) {
                SimpleTestLayout(Modifier.semantics { collapse { true } }) {}
            }
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("merge collapse action") {
                    it.config.getOrNull(SemanticsActions.Collapse)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.action == null
                }
            )
    }

    @Test
    fun mergeSemanticsActions_changeActionLabelNotWork_notMergeDescendants_descendantUnmergeable() {
        val actionLabel = "send"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics {
                        onClick(label = actionLabel, action = null)
                    }
            ) {
                SimpleTestLayout(Modifier.clickable {}) {}
            }
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("merge onClick") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.action == null
                }
            )
    }

    @Test
    fun mergeSemanticsActions_changeActionLabelNotWork_mergeDescendants_descendantUnmergeable() {
        val actionLabel = "send"
        rule.setContent {
            SimpleTestLayout(
                Modifier
                    .testTag(TestTag)
                    .semantics(mergeDescendants = true) {
                        onClick(label = actionLabel, action = null)
                    }
            ) {
                SimpleTestLayout(Modifier.clickable {}) {}
            }
        }
        rule.onNodeWithTag(TestTag)
            .assert(
                SemanticsMatcher("merge onClick") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == actionLabel &&
                        it.config.getOrNull(SemanticsActions.OnClick)?.action == null
                }
            )
    }

    @Test
    fun testInspectorValue() {
        val properties: SemanticsPropertyReceiver.() -> Unit = {}
        rule.setContent {
            val modifier = Modifier.semantics(true, properties) as InspectableValue

            assertThat(modifier.nameFallback).isEqualTo("semantics")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.asIterable()).containsExactly(
                ValueElement("mergeDescendants", true),
                ValueElement("properties", properties)
            )
        }
    }

    @Test
    fun testChildrenAreZSorted() {
        val child1 = "child1"
        val child2 = "child2"
        rule.setContent {
            SimpleTestLayout(
                Modifier.testTag(TestTag).semantics {}
            ) {
                SimpleTestLayout(
                    Modifier.zIndex(1f).semantics { contentDescription = child1 }
                ) {}
                SimpleTestLayout(Modifier.semantics { contentDescription = child2 }) { }
            }
        }

        val root = rule.onNodeWithTag(TestTag).fetchSemanticsNode("can't find node $TestTag")
        assertEquals(2, root.children.size)
        assertEquals(
            child2,
            root.children[0].config.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(
            child1,
            root.children[1].config.getOrNull(SemanticsProperties.ContentDescription)
        )
    }
}

private fun SemanticsNodeInteraction.assertDoesNotHaveProperty(property: SemanticsPropertyKey<*>) {
    assert(SemanticsMatcher.keyNotDefined(property))
}

// Falsely mark the layout counter stable to avoid influencing recomposition behavior
@Stable
private class Counter(var count: Int)

@Composable
private fun CountingLayout(modifier: Modifier, counter: Counter) {
    Layout(
        modifier = modifier,
        content = {},
        measurePolicy = remember {
            MeasurePolicy { _, constraints ->
                counter.count++
                layout(constraints.minWidth, constraints.minHeight) {}
            }
        }
    )
}

/**
 * A simple test layout that does the bare minimum required to lay out an arbitrary number of
 * children reasonably.  Useful for Semantics hierarchy testing
 */
@Composable
private fun SimpleTestLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        if (measurables.isEmpty()) {
            layout(constraints.minWidth, constraints.minHeight) {}
        } else {
            val placeables = measurables.map {
                it.measure(constraints)
            }
            val (width, height) = with(placeables.filterNotNull()) {
                Pair(
                    max(
                        maxByOrNull { it.width }?.width ?: 0,
                        constraints.minWidth
                    ),
                    max(
                        maxByOrNull { it.height }?.height ?: 0,
                        constraints.minHeight
                    )
                )
            }
            layout(width, height) {
                for (placeable in placeables) {
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }
}
