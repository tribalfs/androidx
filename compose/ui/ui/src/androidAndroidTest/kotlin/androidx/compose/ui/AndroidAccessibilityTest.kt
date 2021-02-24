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

package androidx.compose.ui

import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
import android.view.accessibility.AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION
import android.view.accessibility.AccessibilityNodeProvider
import android.view.accessibility.AccessibilityRecord
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.internal.matchers.apachecommons.ReflectionEquals

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class AndroidAccessibilityTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var androidComposeView: AndroidComposeView
    private lateinit var container: OpenComposeView
    private lateinit var delegate: AndroidComposeViewAccessibilityDelegateCompat
    private lateinit var provider: AccessibilityNodeProvider
    private lateinit var textLayoutResult: TextLayoutResult

    private val argument = ArgumentCaptor.forClass(AccessibilityEvent::class.java)
    private var isTextFieldVisible by mutableStateOf(true)
    private var textFieldSelectionOne = false
    private var isPaneVisible by mutableStateOf(false)
    private var paneTestTitle by mutableStateOf(PaneTitleOne)
    private var textFieldValue = mutableStateOf(TextFieldValue(InitialText))

    companion object {
        private const val TimeOutInitialization: Long = 5000
        private const val TopColTag = "topColumn"
        private const val ToggleableTag = "toggleable"
        private const val DisabledToggleableTag = "disabledToggleable"
        private const val TextFieldTag = "textField"
        private const val TextNodeTag = "textNode"
        private const val ParentForOverlappedChildrenTag = "parentForOverlappedChildren"
        private const val OverlappedChildOneTag = "overlappedChildOne"
        private const val OverlappedChildTwoTag = "overlappedChildTwo"
        private const val PaneTag = "pane"
        private const val PaneTitleOne = "pane title one"
        private const val PaneTitleTwo = "pane title two"
        private const val InputText = "hello"
        private const val InitialText = "h"
    }

    @Before
    fun setup() {
        // Use uiAutomation to enable accessibility manager.
        InstrumentationRegistry.getInstrumentation().uiAutomation

        rule.activityRule.scenario.onActivity { activity ->
            container = spy(OpenComposeView(activity)) {
                on { onRequestSendAccessibilityEvent(any(), any()) } doReturn false
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            activity.setContentView(container)
            container.setContent {
                var checked by remember { mutableStateOf(true) }
                var value by remember { textFieldValue }
                Column(Modifier.testTag(TopColTag)) {
                    Box(
                        Modifier
                            .toggleable(value = checked, onValueChange = { checked = it })
                            .testTag(ToggleableTag)
                    ) {
                        BasicText("ToggleableText")
                        Box {
                            BasicText("TextNode", Modifier.testTag(TextNodeTag))
                        }
                    }
                    Box(
                        Modifier
                            .toggleable(
                                value = checked,
                                enabled = false,
                                onValueChange = { checked = it }
                            )
                            .testTag(DisabledToggleableTag),
                        content = {
                            BasicText("ToggleableText")
                        }
                    )
                    Box(Modifier.testTag(ParentForOverlappedChildrenTag)) {
                        BasicText(
                            "Child One",
                            Modifier
                                .zIndex(1f)
                                .testTag(OverlappedChildOneTag)
                                .requiredSize(50.dp)
                        )
                        BasicText(
                            "Child Two",
                            Modifier
                                .testTag(OverlappedChildTwoTag)
                                .requiredSize(50.dp)
                        )
                    }
                    if (isTextFieldVisible) {
                        BasicTextField(
                            modifier = Modifier
                                .semantics {
                                    // Make sure this block will be executed when selection changes.
                                    this.textSelectionRange = value.selection
                                    if (value.selection == TextRange(1)) {
                                        textFieldSelectionOne = true
                                    }
                                }
                                .testTag(TextFieldTag),
                            value = value,
                            onValueChange = { value = it },
                            onTextLayout = { textLayoutResult = it },
                            visualTransformation = PasswordVisualTransformation(),
                            decorationBox = {
                                BasicText("Label")
                                it()
                            }
                        )
                    }
                }
                if (isPaneVisible) {
                    Box(
                        Modifier
                            .testTag(PaneTag)
                            .semantics { paneTitle = paneTestTitle }
                    ) {}
                }
            }
            androidComposeView = container.getChildAt(0) as AndroidComposeView
            delegate = ViewCompat.getAccessibilityDelegate(androidComposeView) as
                AndroidComposeViewAccessibilityDelegateCompat
            delegate.accessibilityForceEnabledForTesting = true
            provider = delegate.getAccessibilityNodeProvider(androidComposeView).provider
                as AccessibilityNodeProvider
        }
    }

    @Test
    fun testCreateAccessibilityNodeInfo() {
        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(toggleableNode.id)
        assertEquals("android.view.View", accessibilityNodeInfo.className)
        val stateDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                accessibilityNodeInfo.stateDescription
            }
            Build.VERSION.SDK_INT >= 19 -> {
                accessibilityNodeInfo.extras.getCharSequence(
                    "androidx.view.accessibility.AccessibilityNodeInfoCompat.STATE_DESCRIPTION_KEY"
                )
            }
            else -> {
                null
            }
        }
        assertEquals("Checked", stateDescription)
        assertTrue(accessibilityNodeInfo.isClickable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
    }

    @Test
    fun testCreateAccessibilityNodeInfo_forTextField() {
        textFieldValue.value = TextFieldValue(InitialText)
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(textFieldNode.id)

        assertEquals("android.widget.EditText", accessibilityNodeInfo.className)
        assertEquals(InitialText, accessibilityNodeInfo.text.toString())
        assertTrue(accessibilityNodeInfo.isFocusable)
        assertFalse(accessibilityNodeInfo.isFocused)
        assertTrue(accessibilityNodeInfo.isEditable)
        assertTrue(accessibilityNodeInfo.isVisibleToUser)
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_CLICK, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_SET_SELECTION, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(ACTION_NEXT_AT_MOVEMENT_GRANULARITY, null)
            )
        )
        assertTrue(
            accessibilityNodeInfo.actionList.contains(
                AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, null
                )
            )
        )
        assertEquals(
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE or
                AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE,
            accessibilityNodeInfo.movementGranularities
        )
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(
                listOf(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY),
                accessibilityNodeInfo.availableExtraData
            )
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun reportedTexts_inTextFieldWithLabel_whenEditableTextNotEmpty() {
        textFieldValue.value = TextFieldValue(InitialText)
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(textFieldNode.id)

        assertEquals(InitialText, accessibilityNodeInfo.text.toString())
        assertEquals("Label", accessibilityNodeInfo.hintText.toString())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun reportedText_inTextFieldWithLabel_whenEditableTextEmpty() {
        textFieldValue.value = TextFieldValue()
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")
        val accessibilityNodeInfo = provider.createAccessibilityNodeInfo(textFieldNode.id)

        assertEquals("Label", accessibilityNodeInfo.text.toString())
        assertEquals(true, accessibilityNodeInfo.isShowingHintText)
    }

    @Test
    fun testPerformAction_succeedOnEnabledNodes() {
        rule.onNodeWithTag(ToggleableTag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(toggleableNode.id, ACTION_CLICK, null))
        }
        rule.onNodeWithTag(ToggleableTag)
            .assertIsOff()

        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")
        rule.runOnUiThread {
            assertTrue(provider.performAction(textFieldNode.id, ACTION_CLICK, null))
        }
        rule.onNodeWithTag(TextFieldTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
        val argument = Bundle()
        argument.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
        argument.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 1)
        rule.runOnUiThread {
            textFieldSelectionOne = false
            assertTrue(provider.performAction(textFieldNode.id, ACTION_SET_SELECTION, argument))
        }

        rule.waitUntil { textFieldSelectionOne }

        rule.onNodeWithTag(TextFieldTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.TextSelectionRange,
                    TextRange(1)
                )
            )
    }

    @Test
    fun testPerformAction_failOnDisabledNodes() {
        rule.onNodeWithTag(DisabledToggleableTag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        val toggleableNode = rule.onNodeWithTag(DisabledToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $DisabledToggleableTag")
        rule.runOnUiThread {
            assertFalse(provider.performAction(toggleableNode.id, ACTION_CLICK, null))
        }
        rule.onNodeWithTag(DisabledToggleableTag)
            .assertIsOn()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun testAddExtraDataToAccessibilityNodeInfo() {
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")
        val info = AccessibilityNodeInfo.obtain()
        val argument = Bundle()
        argument.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
        argument.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
        provider.addExtraDataToAccessibilityNodeInfo(
            textFieldNode.id,
            info,
            AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            argument
        )
        val data = info.extras
            .getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
        assertEquals(1, data!!.size)
        val rectF = data[0] as RectF
        val expectedRect = textLayoutResult.getBoundingBox(0).translate(
            textFieldNode
                .positionInWindow
        )
        assertEquals(expectedRect.left, rectF.left)
        assertEquals(expectedRect.top, rectF.top)
        assertEquals(expectedRect.right, rectF.right)
        assertEquals(expectedRect.bottom, rectF.bottom)
    }

    @Test
    fun sendStateChangeEvent_whenClickToggleable() {
        rule.onNodeWithTag(ToggleableTag)
            .assertIsDisplayed()
            .assertIsOn()

        waitForSubtreeEventToSend()
        rule.onNodeWithTag(ToggleableTag)
            .performClick()
            .assertIsOff()

        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")

        val stateEvent = delegate.createEvent(
            toggleableNode.id,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        )
        stateEvent.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION

        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            assertTrue(containsEvent(values, stateEvent))
        }
    }

    @Test
    fun sendTextEvents_whenSetText() {
        textFieldValue.value = TextFieldValue(InitialText)

        rule.onNodeWithTag(TextFieldTag)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(InitialText)
                )
            )

        waitForSubtreeEventToSend()
        rule.onNodeWithTag(TextFieldTag)
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(InputText)) }
        rule.onNodeWithTag(TextFieldTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString(InputText)
                )
            )

        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")

        val textEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )
        textEvent.fromIndex = InitialText.length
        textEvent.removedCount = 0
        textEvent.addedCount = InputText.length - InitialText.length
        textEvent.beforeText = InitialText
        textEvent.text.add(InputText)

        val selectionEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        )
        selectionEvent.fromIndex = InputText.length
        selectionEvent.toIndex = InputText.length
        selectionEvent.itemCount = InputText.length
        selectionEvent.text.add(InputText)

        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            assertTrue(containsEvent(values, textEvent))
            assertTrue(containsEvent(values, selectionEvent))
        }
    }

    @Test
    @Ignore("b/177656801")
    fun sendSubtreeChangeEvents_whenNodeRemoved() {
        val topColumn = rule.onNodeWithTag(TopColTag)
            .fetchSemanticsNode("couldn't find node with tag $TopColTag")
        rule.onNodeWithTag(TextFieldTag)
            .assertExists()
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == topColumn.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        // TextField is removed compared to setup.
        isTextFieldVisible = false

        rule.onNodeWithTag(TextFieldTag)
            .assertDoesNotExist()
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == topColumn.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    @Ignore("b/178524529")
    fun traverseEventBeforeSelectionEvent_whenTraverseTextField() {
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .assertIsDisplayed()
            .fetchSemanticsNode("couldn't find node with tag $TextFieldTag")

        waitForSubtreeEventToSend()
        val args = Bundle()
        args.putInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER
        )
        args.putBoolean(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, false)
        val provider = delegate.getAccessibilityNodeProvider(androidComposeView).provider as
            AccessibilityNodeProvider
        provider.performAction(
            textFieldNode.id,
            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            args
        )

        val selectionEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        )
        selectionEvent.fromIndex = InitialText.length
        selectionEvent.toIndex = InitialText.length
        selectionEvent.itemCount = InitialText.length
        selectionEvent.text.add(InitialText)

        val traverseEvent = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
        )
        traverseEvent.fromIndex = 0
        traverseEvent.toIndex = 1
        traverseEvent.action = AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
        traverseEvent.movementGranularity =
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER
        traverseEvent.text.add(InitialText)

        rule.runOnIdle {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView), argument.capture()
            )
            val values = argument.allValues
            // Note right now the event ordering is incorrect. The ordering in test needs to be
            // changed when the event ordering if fixed.
            val traverseEventIndex = eventIndex(values, traverseEvent)
            val selectionEventIndex = eventIndex(values, selectionEvent)
            assertNotEquals(-1, traverseEventIndex)
            assertNotEquals(-1, selectionEventIndex)
            assertTrue(traverseEventIndex < selectionEventIndex)
        }
    }

    @Test
    @Ignore("b/177656801")
    fun semanticsNodeBeingMergedLayoutChange_sendThrottledSubtreeEventsForMergedSemanticsNode() {
        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")
        val textNode = rule.onNodeWithTag(TextNodeTag, useUnmergedTree = true)
            .fetchSemanticsNode("couldn't find node with tag $TextNodeTag")
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        rule.runOnUiThread {
            // Directly call onLayoutChange because this guarantees short time.
            for (i in 1..10) {
                delegate.onLayoutChange(textNode.layoutNode)
            }
        }

        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    @Ignore("b/177656801")
    fun layoutNodeWithoutSemanticsLayoutChange_sendThrottledSubtreeEventsForMergedSemanticsNode() {
        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")
        val textNode = rule.onNodeWithTag(TextNodeTag, useUnmergedTree = true)
            .fetchSemanticsNode("couldn't find node with tag $TextNodeTag")
        // wait for the subtree change events from initialization to send
        waitForSubtreeEventToSendAndVerify {
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }

        rule.runOnUiThread {
            // Directly call onLayoutChange because this guarantees short time.
            for (i in 1..10) {
                // layout change for the parent box node
                delegate.onLayoutChange(textNode.layoutNode.parent!!)
            }
        }

        waitForSubtreeEventToSendAndVerify {
            // One from initialization and one from layout changes.
            verify(container, atLeastOnce()).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == toggleableNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            it.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                    }
                )
            )
        }
    }

    @Test
    fun testGetVirtualViewAt() {
        var rootNodeBoundsLeft = 0f
        var rootNodeBoundsTop = 0f
        rule.runOnIdle {
            val rootNode = androidComposeView.semanticsOwner.rootSemanticsNode
            rootNodeBoundsLeft = rootNode.boundsInWindow.left
            rootNodeBoundsTop = rootNode.boundsInWindow.top
        }
        val toggleableNode = rule.onNodeWithTag(ToggleableTag)
            .fetchSemanticsNode("couldn't find node with tag $ToggleableTag")
        val toggleableNodeBounds = toggleableNode.boundsInWindow

        val toggleableNodeId = delegate.getVirtualViewAt(
            (toggleableNodeBounds.left + toggleableNodeBounds.right) / 2 - rootNodeBoundsLeft,
            (toggleableNodeBounds.top + toggleableNodeBounds.bottom) / 2 - rootNodeBoundsTop
        )
        assertEquals(toggleableNode.id, toggleableNodeId)

        val overlappedChildOneNode = rule.onNodeWithTag(OverlappedChildOneTag)
            .fetchSemanticsNode("couldn't find node with tag $OverlappedChildOneTag")
        val overlappedChildTwoNode = rule.onNodeWithTag(OverlappedChildTwoTag)
            .fetchSemanticsNode("couldn't find node with tag $OverlappedChildTwoTag")
        val overlappedChildNodeBounds = overlappedChildTwoNode.boundsInWindow
        val overlappedChildNodeId = delegate.getVirtualViewAt(
            (overlappedChildNodeBounds.left + overlappedChildNodeBounds.right) / 2 -
                rootNodeBoundsLeft,
            (overlappedChildNodeBounds.top + overlappedChildNodeBounds.bottom) / 2 -
                rootNodeBoundsTop
        )
        assertEquals(overlappedChildOneNode.id, overlappedChildNodeId)
        assertNotEquals(overlappedChildTwoNode.id, overlappedChildNodeId)
    }

    @Test
    fun testAccessibilityNodeInfoTreePruned() {
        val parentNode = rule.onNodeWithTag(ParentForOverlappedChildrenTag)
            .fetchSemanticsNode("couldn't find node with tag $ParentForOverlappedChildrenTag")
        val overlappedChildOneNode = rule.onNodeWithTag(OverlappedChildOneTag)
            .fetchSemanticsNode("couldn't find node with tag $OverlappedChildOneTag")
        val overlappedChildTwoNode = rule.onNodeWithTag(OverlappedChildTwoTag)
            .fetchSemanticsNode("couldn't find node with tag $OverlappedChildTwoTag")
        assertEquals(1, provider.createAccessibilityNodeInfo(parentNode.id).childCount)
        assertEquals(
            "Child One",
            provider.createAccessibilityNodeInfo(overlappedChildOneNode.id).text.toString()
        )
        assertNull(provider.createAccessibilityNodeInfo(overlappedChildTwoNode.id))
    }

    @Test
    fun testPaneAppear() {
        rule.onNodeWithTag(PaneTag).assertDoesNotExist()
        isPaneVisible = true
        rule.onNodeWithTag(PaneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    PaneTitleOne
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()
        val paneNode = rule.onNodeWithTag(PaneTag).fetchSemanticsNode()
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == paneNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED
                    }
                )
            )
        }
    }

    @Test
    fun testPaneTitleChange() {
        rule.onNodeWithTag(PaneTag).assertDoesNotExist()
        isPaneVisible = true
        rule.onNodeWithTag(PaneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    PaneTitleOne
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()
        val paneNode = rule.onNodeWithTag(PaneTag).fetchSemanticsNode()
        paneTestTitle = PaneTitleTwo
        rule.onNodeWithTag(PaneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    PaneTitleTwo
                )
            )
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        getAccessibilityEventSourceSemanticsNodeId(it) == paneNode.id &&
                            it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE
                    }
                )
            )
        }
    }

    @Test
    fun testPaneDisappear() {
        rule.onNodeWithTag(PaneTag).assertDoesNotExist()
        isPaneVisible = true
        rule.onNodeWithTag(PaneTag)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    PaneTitleOne
                )
            )
            .assertIsDisplayed()
        waitForSubtreeEventToSend()
        isPaneVisible = false
        rule.onNodeWithTag(PaneTag).assertDoesNotExist()
        rule.runOnIdle {
            verify(container, times(1)).requestSendAccessibilityEvent(
                eq(androidComposeView),
                argThat(
                    ArgumentMatcher {
                        it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                            it.contentChangeTypes ==
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED
                    }
                )
            )
        }
    }

    @Test
    fun testEventForPasswordTextField() {
        val textFieldNode = rule.onNodeWithTag(TextFieldTag)
            .fetchSemanticsNode("Couldn't fetch node with tag $TextFieldTag")

        val event = delegate.createEvent(
            textFieldNode.id,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        )

        assertTrue(event.isPassword)
    }

    private fun eventIndex(list: List<AccessibilityEvent>, event: AccessibilityEvent): Int {
        for (i in list.indices) {
            if (ReflectionEquals(list[i], null).matches(event)) {
                return i
            }
        }
        return -1
    }

    private fun containsEvent(list: List<AccessibilityEvent>, event: AccessibilityEvent): Boolean {
        return eventIndex(list, event) != -1
    }

    private fun getAccessibilityEventSourceSemanticsNodeId(event: AccessibilityEvent): Int {
        val getSourceNodeIdMethod = AccessibilityRecord::class.java
            .getDeclaredMethod("getSourceNodeId")
        getSourceNodeIdMethod.isAccessible = true
        return (getSourceNodeIdMethod.invoke(event) as Long shr 32).toInt()
    }

    private fun waitForSubtreeEventToSendAndVerify(verify: () -> Unit) {
        // TODO(aelias): Make this wait after the 100ms delay to check the second batch is also correct
        rule.waitForIdle()
        verify()
    }

    private fun waitForSubtreeEventToSend() {
        // When the subtree events are sent, we will also update our previousSemanticsNodes,
        // which will affect our next accessibility events from semantics tree comparison.
        rule.mainClock.advanceTimeBy(TimeOutInitialization)
        rule.waitForIdle()
    }
}
