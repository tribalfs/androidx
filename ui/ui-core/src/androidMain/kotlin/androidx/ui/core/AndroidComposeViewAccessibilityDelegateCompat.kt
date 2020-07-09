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

package androidx.ui.core

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.IntRange
import androidx.collection.SparseArrayCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.ui.core.semantics.SemanticsNode
import androidx.ui.core.semantics.findChildById
import androidx.ui.core.semantics.getAllSemanticsNodesToMap
import androidx.ui.core.semantics.getOrNull
import androidx.ui.semantics.CustomAccessibilityAction
import androidx.ui.semantics.SemanticsActions
import androidx.ui.semantics.SemanticsActions.CustomActions
import androidx.ui.semantics.SemanticsProperties
import androidx.ui.util.fastForEach

internal class AndroidComposeViewAccessibilityDelegateCompat(val view: AndroidComposeView) :
    AccessibilityDelegateCompat() {
    companion object {
        /** Virtual node identifier value for invalid nodes. */
        const val InvalidId = Integer.MIN_VALUE
        const val ClassName = "android.view.View"
        /**
         * Intent size limitations prevent sending over a megabyte of data. Limit
         * text length to 100K characters - 200KB.
         */
        const val ParcelSafeTextLength = 100000
        private val AccessibilityActionsResourceIds = intArrayOf(
            R.id.accessibility_custom_action_0,
            R.id.accessibility_custom_action_1,
            R.id.accessibility_custom_action_2,
            R.id.accessibility_custom_action_3,
            R.id.accessibility_custom_action_4,
            R.id.accessibility_custom_action_5,
            R.id.accessibility_custom_action_6,
            R.id.accessibility_custom_action_7,
            R.id.accessibility_custom_action_8,
            R.id.accessibility_custom_action_9,
            R.id.accessibility_custom_action_10,
            R.id.accessibility_custom_action_11,
            R.id.accessibility_custom_action_12,
            R.id.accessibility_custom_action_13,
            R.id.accessibility_custom_action_14,
            R.id.accessibility_custom_action_15,
            R.id.accessibility_custom_action_16,
            R.id.accessibility_custom_action_17,
            R.id.accessibility_custom_action_18,
            R.id.accessibility_custom_action_19,
            R.id.accessibility_custom_action_20,
            R.id.accessibility_custom_action_21,
            R.id.accessibility_custom_action_22,
            R.id.accessibility_custom_action_23,
            R.id.accessibility_custom_action_24,
            R.id.accessibility_custom_action_25,
            R.id.accessibility_custom_action_26,
            R.id.accessibility_custom_action_27,
            R.id.accessibility_custom_action_28,
            R.id.accessibility_custom_action_29,
            R.id.accessibility_custom_action_30,
            R.id.accessibility_custom_action_31
        )
    }
    /** Virtual view id for the currently hovered logical item. */
    private var hoveredVirtualViewId = InvalidId
    private val accessibilityManager: AccessibilityManager =
        view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val handler = Handler(Looper.getMainLooper())
    private var nodeProvider: AccessibilityNodeProviderCompat = MyNodeProvider()
    private var focusedVirtualViewId = InvalidId
    // For actionIdToId and labelToActionId, the keys are the virtualViewIds. The value of
    // actionIdToLabel holds assigned custom action id to custom action label mapping. The
    // value of labelToActionId holds custom action label to assigned custom action id mapping.
    private var actionIdToLabel = SparseArrayCompat<SparseArrayCompat<CharSequence>>()
    private var labelToActionId = SparseArrayCompat<Map<CharSequence, Int>>()

    private class SemanticsNodeCopy(
        semanticsNode: SemanticsNode
    ) {
        val config = semanticsNode.config
        val children: MutableSet<Int> = mutableSetOf()

        init {
            semanticsNode.children.fastForEach { child ->
                children.add(child.id)
            }
        }
    }

    private var semanticsNodes: MutableMap<Int, SemanticsNodeCopy> = mutableMapOf()
    private var semanticsRoot = SemanticsNodeCopy(view.semanticsOwner.rootSemanticsNode)
    private var checkingForSemanticsChanges = false

    init {
        // Remove callbacks that rely on view being attached to a window when we become detached.
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {}
            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(semanticsChangeChecker)
            }
        })
    }

    fun createNodeInfo(virtualViewId: Int):
            AccessibilityNodeInfoCompat {
        val info: AccessibilityNodeInfoCompat = AccessibilityNodeInfoCompat.obtain()
        // the hidden property is often not there
        info.isVisibleToUser = true
        val semanticsNode: SemanticsNode?
        if (virtualViewId == AccessibilityNodeProviderCompat.HOST_VIEW_ID) {
            info.setSource(view)
            semanticsNode = view.semanticsOwner.rootSemanticsNode
            info.setParent(ViewCompat.getParentForAccessibility(view) as? View)
        } else {
            semanticsNode = view.semanticsOwner.rootSemanticsNode.findChildById(virtualViewId)
            if (semanticsNode == null) {
                // throw IllegalStateException("Semantics node $virtualViewId is not attached")
                return info
            }
            info.setSource(view, semanticsNode.id)
            // TODO(b/154023028): Semantics: Immediate children of the root node report parent ==
            // null
            if (semanticsNode.parent != null) {
                var parentId = semanticsNode.parent!!.id
                if (parentId == view.semanticsOwner.rootSemanticsNode.id) {
                    parentId = AccessibilityNodeProviderCompat.HOST_VIEW_ID
                }
                info.setParent(view, parentId)
            } else {
                // throw IllegalStateException("semanticsNode $virtualViewId has null parent")
            }
        }

        // TODO(b/151240295): Should we have widgets class name?
        info.className = ClassName
        info.packageName = view.context.packageName
        try {
            info.setBoundsInScreen(
                android.graphics.Rect(
                    semanticsNode.globalBounds.left.toInt(),
                    semanticsNode.globalBounds.top.toInt(),
                    semanticsNode.globalBounds.right.toInt(),
                    semanticsNode.globalBounds.bottom.toInt()
                )
            )
        } catch (e: IllegalStateException) {
            // We may get "Asking for measurement result of unmeasured layout modifier" error.
            // TODO(b/153198816): check whether we still get this exception when R is in.
            info.setBoundsInScreen(android.graphics.Rect())
        }

        for (child in semanticsNode.children) {
            info.addChild(view, child.id)
        }

        // Manage internal accessibility focus state.
        if (focusedVirtualViewId == virtualViewId) {
            info.isAccessibilityFocused = true
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat
                    .ACTION_CLEAR_ACCESSIBILITY_FOCUS
            )
        } else {
            info.isAccessibilityFocused = false
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat
                    .ACTION_ACCESSIBILITY_FOCUS
            )
        }

        // TODO: we need a AnnotedString to CharSequence conversion function
        info.text = trimToSize(semanticsNode.config.getOrNull(SemanticsProperties.Text)?.text,
            ParcelSafeTextLength)
        info.stateDescription =
            semanticsNode.config.getOrNull(SemanticsProperties.AccessibilityValue)
        info.contentDescription =
            semanticsNode.config.getOrNull(SemanticsProperties.AccessibilityLabel)
        info.isEnabled = semanticsNode.config.getOrElse(SemanticsProperties.Enabled) { true }
        info.isVisibleToUser = !(semanticsNode.config.getOrElse(
            SemanticsProperties.Hidden) { false })
        info.isClickable = semanticsNode.config.contains(SemanticsActions.OnClick)
        if (info.isClickable) {
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK
            )
        }
        if (semanticsNode.config.contains(SemanticsActions.SetProgress)) {
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS
            )
        }
        // TODO(b/157692376): remove scroll forward/backward api together with slider scroll action.
        @Suppress("DEPRECATION")
        if (semanticsNode.config.contains(SemanticsActions.ScrollForward)) {
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD
            )
        }
        @Suppress("DEPRECATION")
        if (semanticsNode.config.contains(SemanticsActions.ScrollBackward)) {
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD
            )
        }

        val rangeInfo =
            semanticsNode.config.getOrNull(SemanticsProperties.AccessibilityRangeInfo)
        if (rangeInfo != null) {
            info.rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
                rangeInfo.range.start, rangeInfo.range.endInclusive, rangeInfo.current
            )
        }

        if (semanticsNode.config.contains(CustomActions)) {
            val customActions = semanticsNode.config[CustomActions]
            if (customActions.size >= AccessibilityActionsResourceIds.size) {
                throw IllegalStateException(
                    "Can't have more than " +
                            "${AccessibilityActionsResourceIds.size} custom actions for one widget"
                )
            }
            val currentActionIdToLabel = SparseArrayCompat<CharSequence>()
            val currentLabelToActionId = mutableMapOf<CharSequence, Int>()
            // If this virtual node had custom action id assignment before, we try to keep the id
            // unchanged for the same action (identified by action label). This way, we can
            // minimize the influence of custom action change between custom actions are
            // presented to the user and actually performed.
            if (labelToActionId.containsKey(virtualViewId)) {
                val oldLabelToActionId = labelToActionId[virtualViewId]
                val availableIds = AccessibilityActionsResourceIds.toMutableList()
                val unassignedActions = mutableListOf<CustomAccessibilityAction>()
                for (action in customActions) {
                    if (oldLabelToActionId!!.contains(action.label)) {
                        val actionId = oldLabelToActionId[action.label]
                        currentActionIdToLabel.put(actionId!!, action.label)
                        currentLabelToActionId[action.label] = actionId
                        availableIds.remove(actionId)
                        info.addAction(
                            AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                actionId, action.label
                            )
                        )
                    } else {
                        unassignedActions.add(action)
                    }
                }
                for ((index, action) in unassignedActions.withIndex()) {
                    val actionId = availableIds[index]
                    currentActionIdToLabel.put(actionId, action.label)
                    currentLabelToActionId[action.label] = actionId
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            actionId, action.label
                        )
                    )
                }
            } else {
                for ((index, action) in customActions.withIndex()) {
                    val actionId = AccessibilityActionsResourceIds[index]
                    currentActionIdToLabel.put(actionId, action.label)
                    currentLabelToActionId[action.label] = actionId
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            actionId, action.label
                        )
                    )
                }
            }
            actionIdToLabel.put(virtualViewId, currentActionIdToLabel)
            labelToActionId.put(virtualViewId, currentLabelToActionId)
        }

        return info
    }

    /**
     * Returns whether this virtual view is accessibility focused.
     *
     * @return True if the view is accessibility focused.
     */
    private fun isAccessibilityFocused(virtualViewId: Int): Boolean {
        return (focusedVirtualViewId == virtualViewId)
    }

    /**
     * Attempts to give accessibility focus to a virtual view.
     * <p>
     * A virtual view will not actually take focus if
     * {@link AccessibilityManager#isEnabled()} returns false,
     * {@link AccessibilityManager#isTouchExplorationEnabled()} returns false,
     * or the view already has accessibility focus.
     *
     * @param virtualViewId The id of the virtual view on which to place
     *            accessibility focus.
     * @return Whether this virtual view actually took accessibility focus.
     */
    private fun requestAccessibilityFocus(virtualViewId: Int): Boolean {
        if (!accessibilityManager.isEnabled ||
            !accessibilityManager.isTouchExplorationEnabled
        ) {
            return false
        }
        // TODO: Check virtual view visibility.
        if (!isAccessibilityFocused(virtualViewId)) {
            // Clear focus from the previously focused view, if applicable.
            if (focusedVirtualViewId != InvalidId) {
                sendEventForVirtualView(
                    focusedVirtualViewId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                    null,
                    null
                )
            }

            // Set focus on the new view.
            focusedVirtualViewId = virtualViewId

            view.invalidate()
            sendEventForVirtualView(
                virtualViewId,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                null,
                null
            )
            return true
        }
        return false
    }

    /**
     * Populates an event of the specified type with information about an item
     * and attempts to send it up through the view hierarchy.
     * <p>
     * You should call this method after performing a user action that normally
     * fires an accessibility event, such as clicking on an item.
     *
     * <pre>public performItemClick(T item) {
     *   ...
     *   sendEventForVirtualView(item.id, AccessibilityEvent.TYPE_VIEW_CLICKED)
     * }
     * </pre>
     *
     * @param virtualViewId The virtual view id for which to send an event.
     * @param eventType The type of event to send.
     * @param contentChangeType The contentChangeType of this event.
     * @param contentDescription Content description of this event.
     * @return true if the event was sent successfully.
     */
    private fun sendEventForVirtualView(
        virtualViewId: Int,
        eventType: Int,
        contentChangeType: Int? = null,
        contentDescription: CharSequence? = null
    ): Boolean {
        if ((virtualViewId == InvalidId) || !accessibilityManager.isEnabled) {
            return false
        }

        val parent: ViewParent = view.parent

        val event: AccessibilityEvent = createEvent(virtualViewId, eventType)
        if (contentChangeType != null) {
            event.contentChangeTypes = contentChangeType
        }
        if (contentDescription != null) {
            event.contentDescription = contentDescription
        }

        return parent.requestSendAccessibilityEvent(view, event)
    }

    /**
     * Constructs and returns an {@link AccessibilityEvent} populated with
     * information about the specified item.
     *
     * @param virtualViewId The virtual view id for the item for which to
     *            construct an event.
     * @param eventType The type of event to construct.
     * @return An {@link AccessibilityEvent} populated with information about
     *         the specified item.
     */
    private fun createEvent(virtualViewId: Int, eventType: Int): AccessibilityEvent {
        val event: AccessibilityEvent = AccessibilityEvent.obtain(eventType)
        event.isEnabled = true
        event.className = ClassName

        // Don't allow the client to override these properties.
        event.packageName = view.context.packageName
        event.setSource(view, virtualViewId)

        return event
    }

    /**
     * Attempts to clear accessibility focus from a virtual view.
     *
     * @param virtualViewId The id of the virtual view from which to clear
     *            accessibility focus.
     * @return Whether this virtual view actually cleared accessibility focus.
     */
    private fun clearAccessibilityFocus(virtualViewId: Int): Boolean {
        if (isAccessibilityFocused(virtualViewId)) {
            focusedVirtualViewId = InvalidId
            view.invalidate()
            sendEventForVirtualView(
                virtualViewId,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                null,
                null
            )
            return true
        }
        return false
    }

    fun performActionHelper(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        val node: SemanticsNode
        if (virtualViewId == AccessibilityNodeProviderCompat.HOST_VIEW_ID) {
            node = view.semanticsOwner.rootSemanticsNode
        } else {
            node = view.semanticsOwner.rootSemanticsNode.findChildById(virtualViewId)
                ?: return false
        }
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS ->
                return requestAccessibilityFocus(virtualViewId)
            AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS ->
                return clearAccessibilityFocus(virtualViewId)
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                return if (node.canPerformAction(SemanticsActions.OnClick)) {
                    node.config[SemanticsActions.OnClick].action()
                } else {
                    false
                }
            }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                // TODO(b/157692376): remove scroll forward/backward api together with slider
                //  scroll action.
                @Suppress("DEPRECATION")
                return if (node.canPerformAction(SemanticsActions.ScrollForward)) {
                    node.config[SemanticsActions.ScrollForward].action()
                } else {
                    false
                }
            }
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                @Suppress("DEPRECATION")
                return if (node.canPerformAction(SemanticsActions.ScrollBackward)) {
                    node.config[SemanticsActions.ScrollBackward].action()
                } else {
                    false
                }
            }
            android.R.id.accessibilityActionSetProgress -> {
                if (arguments == null || !arguments.containsKey(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE
                    )
                ) {
                    return false
                }
                return if (node.canPerformAction(SemanticsActions.SetProgress)) {
                    node.config[SemanticsActions.SetProgress].action(
                        arguments.getFloat(
                            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE
                        )
                    )
                } else {
                    false
                }
            }
            // TODO: handling for other system actions
            else -> {
                val label = actionIdToLabel[virtualViewId]?.get(action) ?: return false
                val customActions = node.config.getOrNull(CustomActions) ?: return false
                for (customAction in customActions) {
                    if (customAction.label == label) {
                        return customAction.action()
                    }
                }
                return false
            }
        }
    }

    /**
     * Dispatches hover {@link android.view.MotionEvent}s to the virtual view hierarchy when
     * the Explore by Touch feature is enabled.
     * <p>
     * This method should be called by overriding
     * {@link View#dispatchHoverEvent}:
     *
     * <pre>&#64;Override
     * public boolean dispatchHoverEvent(MotionEvent event) {
     *   if (mHelper.dispatchHoverEvent(this, event) {
     *     return true;
     *   }
     *   return super.dispatchHoverEvent(event);
     * }
     * </pre>
     *
     * @param event The hover event to dispatch to the virtual view hierarchy.
     * @return Whether the hover event was handled.
     */
    fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (!accessibilityManager.isEnabled() ||
            !accessibilityManager.isTouchExplorationEnabled()) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                val virtualViewId: Int = getVirtualViewAt(event.getX(), event.getY())
                updateHoveredVirtualView(virtualViewId)
                return (virtualViewId != InvalidId)
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hoveredVirtualViewId != InvalidId) {
                    updateHoveredVirtualView(InvalidId)
                    return true
                }
                return false
            }
            else -> {
                return false
            }
        }
    }

    private fun getVirtualViewAt(x: Float, y: Float): Int {
        val node = view.semanticsOwner.rootSemanticsNode
        val id = findVirtualViewAt(x + node.globalBounds.left,
            y + node.globalBounds.top, node)
        if (id == node.id) {
            return AccessibilityNodeProviderCompat.HOST_VIEW_ID
        }
        return id
    }

    // TODO(b/151729467): compose accessibility getVirtualViewAt needs to be more efficient
    private fun findVirtualViewAt(x: Float, y: Float, node: SemanticsNode): Int {
        node.children.fastForEach {
            val id = findVirtualViewAt(x, y, it)
            if (id != InvalidId) {
                return id
            }
        }

        if (node.globalBounds.left < x && node.globalBounds.right > x && node
                .globalBounds.top < y && node.globalBounds.bottom > y) {
            return node.id
        }

        return InvalidId
    }

    /**
     * Sets the currently hovered item, sending hover accessibility events as
     * necessary to maintain the correct state.
     *
     * @param virtualViewId The virtual view id for the item currently being
     *            hovered, or {@link #InvalidId} if no item is hovered within
     *            the parent view.
     */
    private fun updateHoveredVirtualView(virtualViewId: Int) {
        if (hoveredVirtualViewId == virtualViewId) {
            return
        }

        val previousVirtualViewId: Int = hoveredVirtualViewId
        hoveredVirtualViewId = virtualViewId

        /*
        Stay consistent with framework behavior by sending ENTER/EXIT pairs
        in reverse order. This is accurate as of API 18.
        */
        sendEventForVirtualView(
            virtualViewId,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            null,
            null
        )
        sendEventForVirtualView(
            previousVirtualViewId,
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT,
            null,
            null
        )
    }

    override fun getAccessibilityNodeProvider(host: View?): AccessibilityNodeProviderCompat {
        return nodeProvider
    }

    /**
     * Trims the text to [size] length. Returns the string as it is if the length is
     * smaller than [size]. If chars at [size] - 1 and [size] is a surrogate
     * pair, returns a CharSequence of length [size] - 1.
     *
     * @param size length of the result, should be greater than 0
     */
    private fun <T : CharSequence> trimToSize(text: T?, @IntRange(from = 1) size: Int): T? {
        require(size > 0)
        var len = size
        if (text.isNullOrEmpty() || text.length <= size) return text
        if (Character.isHighSurrogate(text[size - 1]) && Character.isLowSurrogate(text[size])) {
            len = size - 1
        }
        @Suppress("UNCHECKED_CAST")
        return text.subSequence(0, len) as T
    }

    // TODO (in a separate cl): Called when the SemanticsNode with id semanticsNodeId disappears.
    // fun clearNode(semanticsNodeId: Int) { // clear the actionIdToId and labelToActionId nodes }

    private val semanticsChangeChecker = Runnable {
        checkForSemanticsChanges()
        checkingForSemanticsChanges = false
    }

    internal fun onSemanticsChange() {
        if (accessibilityManager.isEnabled && !checkingForSemanticsChanges) {
            checkingForSemanticsChanges = true
            handler.post(semanticsChangeChecker)
        }
    }

    private fun checkForSemanticsChanges() {
        val newSemanticsNodes = view.semanticsOwner.getAllSemanticsNodesToMap()

        // Structural change
        sendSemanticsStructureChangeEvents(view.semanticsOwner.rootSemanticsNode, semanticsRoot)

        // Property change
        sendSemanticsPropertyChangeEvents(newSemanticsNodes)

        // Update the cache
        semanticsNodes.clear()
        for (entry in newSemanticsNodes.entries) {
            semanticsNodes[entry.key] = SemanticsNodeCopy(entry.value)
        }
        semanticsRoot = SemanticsNodeCopy(view.semanticsOwner.rootSemanticsNode)
    }

    private fun sendSemanticsPropertyChangeEvents(newSemanticsNodes: Map<Int, SemanticsNode>) {
        for (id in newSemanticsNodes.keys) {
            if (!semanticsNodes.contains(id)) {
                continue
            }

            // We do doing this search because the new configuration is set as a whole, so we
            // can't indicate which property is changed when setting the new configuration.
            val newNode = newSemanticsNodes[id]
            val oldNode = semanticsNodes[id]
            for (entry in newNode!!.config) {
                if (entry.value == oldNode!!.config.getOrNull(entry.key)) {
                    continue
                }
                when (entry.key) {
                    SemanticsProperties.AccessibilityValue ->
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                        )
                    SemanticsProperties.AccessibilityLabel ->
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION,
                            entry.value as CharSequence
                        )
                    else -> {
                        // TODO(b/151840490) send the correct events when property changes
                    }
                }
            }
        }
    }

    private fun sendSemanticsStructureChangeEvents(
        newNode: SemanticsNode,
        oldNode: SemanticsNodeCopy
    ) {
        val newChildren: MutableSet<Int> = mutableSetOf()

        // If any child is added, clear the subtree rooted at this node and return.
        newNode.children.fastForEach { child ->
            if (!oldNode.children.contains(child.id)) {
                sendEventForVirtualView(
                    semanticsNodeIdToAccessibilityVirtualNodeId(newNode.id),
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
                    null
                )
                return
            }
            newChildren.add(child.id)
        }

        // If any child is deleted, clear the subtree rooted at this node and return.
        for (child in oldNode.children) {
            if (!newChildren.contains(child)) {
                sendEventForVirtualView(
                    semanticsNodeIdToAccessibilityVirtualNodeId(newNode.id),
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE,
                    null
                )
                return
            }
        }

        newNode.children.fastForEach { child ->
            sendSemanticsStructureChangeEvents(child, semanticsNodes[child.id]!!)
        }
    }

    private fun semanticsNodeIdToAccessibilityVirtualNodeId(id: Int): Int {
        if (id == view.semanticsOwner.rootSemanticsNode.id) {
            return AccessibilityNodeProviderCompat.HOST_VIEW_ID
        }
        return id
    }

    inner class MyNodeProvider() : AccessibilityNodeProviderCompat() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int):
                AccessibilityNodeInfoCompat? {
            return createNodeInfo(virtualViewId)
        }

        override fun performAction(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            return performActionHelper(virtualViewId, action, arguments)
        }
    }
}
