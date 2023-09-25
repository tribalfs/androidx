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

package androidx.compose.ui.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.util.Log
import android.util.LongSparseArray
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH
import android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX
import android.view.accessibility.AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
import android.view.accessibility.AccessibilityNodeProvider
import android.view.translation.TranslationRequestValue
import android.view.translation.ViewTranslationRequest
import android.view.translation.ViewTranslationResponse
import androidx.annotation.DoNotInline
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import androidx.collection.SparseArrayCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.HitTestResult
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.OwnerScope
import androidx.compose.ui.node.requireLayoutNode
import androidx.compose.ui.platform.accessibility.hasCollectionInfo
import androidx.compose.ui.platform.accessibility.setCollectionInfo
import androidx.compose.ui.platform.accessibility.setCollectionItemInfo
import androidx.compose.ui.platform.coreshims.ContentCaptureSessionCompat
import androidx.compose.ui.platform.coreshims.ViewCompatShims
import androidx.compose.ui.platform.coreshims.ViewStructureCompat
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsActions.CustomActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.URLSpanCache
import androidx.compose.ui.text.platform.toAccessibilitySpannableString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMap
import androidx.core.util.keyIterator
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
import androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

private fun LayoutNode.findClosestParentNode(selector: (LayoutNode) -> Boolean): LayoutNode? {
    var currentParent = this.parent
    while (currentParent != null) {
        if (selector(currentParent)) {
            return currentParent
        } else {
            currentParent = currentParent.parent
        }
    }

    return null
}

@OptIn(InternalTextApi::class)
internal class AndroidComposeViewAccessibilityDelegateCompat(val view: AndroidComposeView) :
    AccessibilityDelegateCompat(),
    DefaultLifecycleObserver {
    companion object {
        /** Virtual node identifier value for invalid nodes. */
        const val InvalidId = Integer.MIN_VALUE
        const val ClassName = "android.view.View"
        const val TextFieldClassName = "android.widget.EditText"
        const val TextClassName = "android.widget.TextView"
        const val LogTag = "AccessibilityDelegate"
        const val ExtraDataTestTagKey = "androidx.compose.ui.semantics.testTag"
        const val ExtraDataIdKey = "androidx.compose.ui.semantics.id"

        /**
         * Intent size limitations prevent sending over a megabyte of data. Limit
         * text length to 100K characters - 200KB.
         */
        const val ParcelSafeTextLength = 100000

        /**
         * The undefined cursor position.
         */
        const val AccessibilityCursorPositionUndefined = -1

        // 20 is taken from AbsSeekbar.java.
        const val AccessibilitySliderStepsCount = 20

        /**
         * Delay before dispatching a recurring accessibility event in milliseconds.
         * This delay guarantees that a recurring event will be send at most once
         * during the [SendRecurringAccessibilityEventsIntervalMillis] time
         * frame.
         */
        const val SendRecurringAccessibilityEventsIntervalMillis: Long = 100

        /**
         * Timeout to determine whether a text selection changed event and the pending text
         * traversed event could be resulted from the same traverse action.
         */
        const val TextTraversedEventTimeoutMillis: Long = 1000
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
    internal var hoveredVirtualViewId = InvalidId

    @VisibleForTesting
    internal val accessibilityManager: AccessibilityManager =
        view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    internal var accessibilityForceEnabledForTesting = false

    @VisibleForTesting
    internal val enabledStateListener: AccessibilityStateChangeListener =
        AccessibilityStateChangeListener { enabled ->
            enabledServices = if (enabled) {
                accessibilityManager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )
            } else {
                emptyList()
            }
        }

    @VisibleForTesting
    internal val touchExplorationStateListener: TouchExplorationStateChangeListener =
        TouchExplorationStateChangeListener {
            enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
        }
    private var enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )

    /**
     * True if any accessibility service enabled in the system, or if any content capture service
     * enabled in the system.
     */
    @VisibleForTesting
    internal val isEnabled: Boolean
        get() {
            return isEnabledForAccessibility || isEnabledForContentCapture
        }

    /**
     * True if any accessibility service enabled in the system, except the UIAutomator (as it
     * doesn't appear in the list of enabled services)
     */
    internal val isEnabledForAccessibility: Boolean
        get() {
            // checking the list allows us to filter out the UIAutomator which doesn't appear in it
            return accessibilityForceEnabledForTesting ||
                accessibilityManager.isEnabled && enabledServices.isNotEmpty()
        }

    /**
     * True if any content capture service enabled in the system.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private val isEnabledForContentCapture: Boolean
        get() {
            if (DisableContentCapture) {
                return false
            }
            return contentCaptureSession != null || contentCaptureForceEnabledForTesting
        }

    /**
     * Indicates whether the translated information is show or hide in the [AndroidComposeView].
     *
     * See [ViewTranslationCallback](https://cs.android.com/android/platform/superproject/+/refs/heads/master:frameworks/base/core/java/android/view/translation/ViewTranslationCallback.java)
     * for more details of the View translation API.
     */
    enum class TranslateStatus { SHOW_ORIGINAL, SHOW_TRANSLATED }
    private var translateStatus = TranslateStatus.SHOW_ORIGINAL

    /**
     * True if accessibility service with the touch exploration (e.g. Talkback) is enabled in the
     * system.
     * Note that UIAutomator doesn't request touch exploration therefore returns false
     */
    private val isTouchExplorationEnabled
        get() = accessibilityForceEnabledForTesting ||
            accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled

    private val handler = Handler(Looper.getMainLooper())
    private var nodeProvider: AccessibilityNodeProviderCompat =
        AccessibilityNodeProviderCompat(MyNodeProvider())
    private var focusedVirtualViewId = InvalidId

    // For actionIdToId and labelToActionId, the keys are the virtualViewIds. The value of
    // actionIdToLabel holds assigned custom action id to custom action label mapping. The
    // value of labelToActionId holds custom action label to assigned custom action id mapping.
    private var actionIdToLabel = SparseArrayCompat<SparseArrayCompat<CharSequence>>()
    private var labelToActionId = SparseArrayCompat<Map<CharSequence, Int>>()
    private var accessibilityCursorPosition = AccessibilityCursorPositionUndefined

    // We hold this node id to reset the [accessibilityCursorPosition] to undefined when
    // traversal with granularity switches to the next node
    private var previousTraversedNode: Int? = null
    private val subtreeChangedLayoutNodes = ArraySet<LayoutNode>()
    private val boundsUpdateChannel = Channel<Unit>(Channel.CONFLATED)
    private var currentSemanticsNodesInvalidated = true

    internal var contentCaptureForceEnabledForTesting = false
    internal var contentCaptureSession: ContentCaptureSessionCompat? = null
    internal val bufferedContentCaptureAppearedNodes = ArrayMap<Int, ViewStructureCompat>()
    internal val bufferedContentCaptureDisappearedNodes = ArraySet<Int>()

    private class PendingTextTraversedEvent(
        val node: SemanticsNode,
        val action: Int,
        val granularity: Int,
        val fromIndex: Int,
        val toIndex: Int,
        val traverseTime: Long
    )

    private var pendingTextTraversedEvent: PendingTextTraversedEvent? = null

    /**
     * Up to date semantics nodes in pruned semantics tree. It always reflects the current
     * semantics tree. They key is the virtual view id(the root node has a key of
     * AccessibilityNodeProviderCompat.HOST_VIEW_ID and other node has a key of its id).
     */
    internal var currentSemanticsNodes: Map<Int, SemanticsNodeWithAdjustedBounds> = mapOf()
        get() {
            if (currentSemanticsNodesInvalidated) { // first instance of retrieving all nodes
                currentSemanticsNodesInvalidated = false
                field = view.semanticsOwner.getAllUncoveredSemanticsNodesToMap()
                if (isEnabledForAccessibility) {
                    setTraversalValues()
                }
            }
            return field
        }
    private var paneDisplayed = ArraySet<Int>()

    internal var idToBeforeMap = HashMap<Int, Int>()
    internal var idToAfterMap = HashMap<Int, Int>()
    internal val EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL =
        "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"
    internal val EXTRA_DATA_TEST_TRAVERSALAFTER_VAL =
        "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALAFTER_VAL"

    private val urlSpanCache = URLSpanCache()

    /**
     * A snapshot of the semantics node. The children here is fixed and are taken from the time
     * this node is constructed. While a SemanticsNode always contains the up-to-date children.
     */
    @VisibleForTesting
    internal class SemanticsNodeCopy(
        val semanticsNode: SemanticsNode,
        currentSemanticsNodes: Map<Int, SemanticsNodeWithAdjustedBounds>
    ) {
        val unmergedConfig = semanticsNode.unmergedConfig
        val children: MutableSet<Int> = mutableSetOf()

        init {
            semanticsNode.replacedChildren.fastForEach { child ->
                if (currentSemanticsNodes.contains(child.id)) {
                    children.add(child.id)
                }
            }
        }

        fun hasPaneTitle() = unmergedConfig.contains(SemanticsProperties.PaneTitle)
    }

    // previousSemanticsNodes holds the previous pruned semantics tree so that we can compare the
    // current and previous trees in onSemanticsChange(). We use SemanticsNodeCopy here because
    // SemanticsNode's children are dynamically generated and always reflect the current children.
    // We need to keep a copy of its old structure for comparison.
    @VisibleForTesting
    internal var previousSemanticsNodes: MutableMap<Int, SemanticsNodeCopy> = mutableMapOf()
    private var previousSemanticsRoot =
        SemanticsNodeCopy(view.semanticsOwner.unmergedRootSemanticsNode, mapOf())
    private var checkingForSemanticsChanges = false

    init {
        // Remove callbacks that rely on view being attached to a window when we become detached.
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                accessibilityManager.addAccessibilityStateChangeListener(enabledStateListener)
                accessibilityManager.addTouchExplorationStateChangeListener(
                    touchExplorationStateListener
                )
                contentCaptureSession = view.getContentCaptureSessionCompat()
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(semanticsChangeChecker)

                accessibilityManager.removeAccessibilityStateChangeListener(enabledStateListener)
                accessibilityManager.removeTouchExplorationStateChangeListener(
                    touchExplorationStateListener
                )
                contentCaptureSession = null
            }
        })
    }

    override fun onStart(owner: LifecycleOwner) {
        initContentCapture(onStart = true)
    }

    override fun onStop(owner: LifecycleOwner) {
        initContentCapture(onStart = false)
    }

    /**
     * Returns true if there is any semantics node in the tree that can scroll in the given
     * [orientation][vertical] and [direction] at the given [position] in the view associated with
     * this delegate.
     *
     * @param direction The direction to check for scrolling: <0 means scrolling left or up, >0
     * means scrolling right or down.
     * @param position The position in the view to check in view-local coordinates.
     */
    internal fun canScroll(
        vertical: Boolean,
        direction: Int,
        position: Offset
    ): Boolean = canScroll(currentSemanticsNodes.values, vertical, direction, position)

    @VisibleForTesting
    internal fun canScroll(
        currentSemanticsNodes: Collection<SemanticsNodeWithAdjustedBounds>,
        vertical: Boolean,
        direction: Int,
        position: Offset
    ): Boolean {
        // No down event has occurred yet which gives us a location to hit test.
        if (position == Offset.Unspecified || !position.isValid()) return false

        val scrollRangeProperty = when (vertical) {
            true -> SemanticsProperties.VerticalScrollAxisRange
            false -> SemanticsProperties.HorizontalScrollAxisRange
        }

        return currentSemanticsNodes.any { node ->
            // Only consider nodes that are under the touch event. Checks the adjusted bounds to
            // avoid overlapping siblings. Because position is a float (touch event can happen in-
            // between pixels), convert the int-based Android Rect to a float-based Compose Rect
            // before doing the comparison.
            if (!node.adjustedBounds.toComposeRect().contains(position)) {
                return@any false
            }

            val scrollRange = node.semanticsNode.config.getOrNull(scrollRangeProperty)
                ?: return@any false

            // A node simply having scrollable semantics doesn't mean it's necessarily scrollable
            // in the given direction – it must also not be scrolled to its limit in that direction.
            var actualDirection = if (scrollRange.reverseScrolling) -direction else direction
            if (direction == 0 && scrollRange.reverseScrolling) {
                // The View implementation of canScroll* treat zero as a positive direction, so
                // this code should do the same. That means if scrolling is reversed, zero should be
                // a negative direction. The actual number doesn't matter, just its sign.
                actualDirection = -1
            }

            if (actualDirection < 0) scrollRange.value() > 0
            else scrollRange.value() < scrollRange.maxValue()
        }
    }

    private fun createNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (view.viewTreeOwners?.lifecycleOwner?.lifecycle?.currentState ==
            Lifecycle.State.DESTROYED
        ) {
            return null
        }
        val info: AccessibilityNodeInfoCompat = AccessibilityNodeInfoCompat.obtain()
        val semanticsNodeWithAdjustedBounds = currentSemanticsNodes[virtualViewId]
        if (semanticsNodeWithAdjustedBounds == null) {
            return null
        }
        val semanticsNode: SemanticsNode = semanticsNodeWithAdjustedBounds.semanticsNode
        if (virtualViewId == AccessibilityNodeProviderCompat.HOST_VIEW_ID) {
            info.setParent(ViewCompat.getParentForAccessibility(view) as? View)
        } else {
            if (semanticsNode.parent != null) {
                var parentId = semanticsNode.parent!!.id
                if (parentId == view.semanticsOwner.unmergedRootSemanticsNode.id) {
                    parentId = AccessibilityNodeProviderCompat.HOST_VIEW_ID
                }
                info.setParent(view, parentId)
            } else {
                throw IllegalStateException("semanticsNode $virtualViewId has null parent")
            }
        }
        info.setSource(view, virtualViewId)
        val boundsInRoot = semanticsNodeWithAdjustedBounds.adjustedBounds
        val topLeftInScreen =
            view.localToScreen(Offset(boundsInRoot.left.toFloat(), boundsInRoot.top.toFloat()))
        val bottomRightInScreen =
            view.localToScreen(Offset(boundsInRoot.right.toFloat(), boundsInRoot.bottom.toFloat()))
        info.setBoundsInScreen(
            android.graphics.Rect(
                floor(topLeftInScreen.x).toInt(),
                floor(topLeftInScreen.y).toInt(),
                ceil(bottomRightInScreen.x).toInt(),
                ceil(bottomRightInScreen.y).toInt()
            )
        )

        populateAccessibilityNodeInfoProperties(virtualViewId, info, semanticsNode)

        return info.unwrap()
    }

    object TopBottomBoundsComparator : Comparator<Pair<Rect, MutableList<SemanticsNode>>> {
        override fun compare(
            a: Pair<Rect, MutableList<SemanticsNode>>,
            b: Pair<Rect, MutableList<SemanticsNode>>
        ): Int {
            val r = a.first.top.compareTo(b.first.top)
            if (r != 0) return r
            return a.first.bottom.compareTo(b.first.bottom)
        }
    }

    object LtrBoundsComparator : Comparator<SemanticsNode> {
        override fun compare(a: SemanticsNode, b: SemanticsNode): Int {
            // TODO: boundsInWindow is quite expensive and allocates several objects,
            // we need to fix this since this is called during sorting
            val ab = a.boundsInWindow
            val bb = b.boundsInWindow
            var r = ab.left.compareTo(bb.left)
            if (r != 0) return r
            r = ab.top.compareTo(bb.top)
            if (r != 0) return r
            r = ab.bottom.compareTo(bb.bottom)
            if (r != 0) return r
            return ab.right.compareTo(bb.right)
        }
    }

    object RtlBoundsComparator : Comparator<SemanticsNode> {
        override fun compare(a: SemanticsNode, b: SemanticsNode): Int {
            // TODO: boundsInWindow is quite expensive and allocates several objects,
            // we need to fix this since this is called during sorting
            val ab = a.boundsInWindow
            val bb = b.boundsInWindow
            var r = ab.right.compareTo(bb.right)
            if (r != 0) return r
            r = ab.top.compareTo(bb.top)
            if (r != 0) return r
            r = ab.bottom.compareTo(bb.bottom)
            if (r != 0) return r
            return ab.left.compareTo(bb.left)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun semanticComparator(layoutIsRtl: Boolean): Comparator<SemanticsNode> {
        return (if (layoutIsRtl) RtlBoundsComparator else LtrBoundsComparator)
            // then compare by layoutNode's zIndex and placement order
            .thenBy(LayoutNode.ZComparator) { it.layoutNode }
            // then compare by semanticsId to break the tie somehow
            .thenBy { it.id }
    }

    /**
     * Returns the results of geometry groupings, which is determined from 1) grouping nodes into
     * distinct, non-overlapping rows based on their top/bottom coordinates, then 2) sorting nodes
     * within each row with the semantics comparator.
     *
     * This method approaches traversal order with more nuance than an approach considering only
     * just hierarchy or only just an individual node's bounds.
     *
     * If [containerChildrenMapping] exists, there are additional children to add, as well as the
     * sorted parent itself
     */
    private fun sortByGeometryGroupings(
        layoutIsRtl: Boolean,
        parentListToSort: ArrayList<SemanticsNode>,
        containerChildrenMapping: MutableMap<Int, MutableList<SemanticsNode>> = mutableMapOf()
    ): MutableList<SemanticsNode> {
        // RowGroupings list consists of pairs, first = a rectangle of the bounds of the row
        // and second = the list of nodes in that row
        val rowGroupings = ArrayList<Pair<Rect, MutableList<SemanticsNode>>>()

        // check to see if this entry overlaps with any groupings in rowGroupings
        fun placedEntryRowOverlaps(
            node: SemanticsNode
        ): Boolean {
            // Conversion to long is needed in order to utilize `until`, which has no float ver
            val entryTopCoord = node.boundsInWindow.top
            val entryBottomCoord = node.boundsInWindow.bottom
            val entryIsEmpty = entryTopCoord >= entryBottomCoord

            for (currIndex in 0..rowGroupings.lastIndex) {
                val currRect = rowGroupings[currIndex].first
                val groupIsEmpty = currRect.top >= currRect.bottom
                val groupOverlapsEntry = !entryIsEmpty && !groupIsEmpty &&
                        max(entryTopCoord, currRect.top) < min(entryBottomCoord, currRect.bottom)

                // If it overlaps with this row group, update cover and add node
                if (groupOverlapsEntry) {
                    val newRect = currRect.intersect(
                        0f,
                        entryTopCoord,
                        Float.POSITIVE_INFINITY,
                        entryBottomCoord
                    )
                    // Replace the cover rectangle, copying over the old list of nodes
                    rowGroupings[currIndex] = Pair(newRect, rowGroupings[currIndex].second)
                    // Add current node
                    rowGroupings[currIndex].second.add(node)
                    // We've found an overlapping group, return true
                    return true
                }
            }

            // If we've made it here, then there are no groups our entry overlaps with
            return false
        }

        for (entryIndex in 0..parentListToSort.lastIndex) {
            val currEntry = parentListToSort[entryIndex]
            // If this is the first entry, or vertical groups don't overlap
            if (entryIndex == 0 || !placedEntryRowOverlaps(currEntry)) {
                val newRect = currEntry.boundsInWindow
                rowGroupings.add(Pair(newRect, mutableListOf(currEntry)))
            } // otherwise, we've already iterated through, found and placed it in a matching group
        }

        // Sort the rows from top to bottom
        rowGroupings.sortWith(TopBottomBoundsComparator)

        val returnList = ArrayList<SemanticsNode>()
        rowGroupings.fastForEach { row ->
            // Sort each individual row's parent nodes
            row.second.sortWith(semanticComparator(layoutIsRtl))
            returnList.addAll(row.second)
        }

        // Kotlin `sortWith` should just pull out the highest traversal indices, but keep everything
        // else in place
        returnList.sortWith { a, b -> a.traversalIndex.compareTo(b.traversalIndex) }

        var i = 0
        // Afterwards, go in and add the containers' children.
        while (i <= returnList.lastIndex) {
            val currNodeId = returnList[i].id
            // If a parent node is a container, then add its children.
            // Add all container's children after the container itself.
            // Because we've already recursed on the containers children, the children should
            // also be sorted by their traversal index
            val containersChildrenList = containerChildrenMapping[currNodeId]
            if (containersChildrenList != null) {
                val containerIsScreenReaderFocusable = isScreenReaderFocusable(returnList[i])
                if (!containerIsScreenReaderFocusable) {
                    // Container is removed if it is not screenreader-focusable
                    returnList.removeAt(i)
                } else {
                    // Increase counter if the container was not removed
                    i += 1
                }
                // Add all the container's children and increase counter by the number of children
                returnList.addAll(i, containersChildrenList)
                i += containersChildrenList.size
            } else {
                // Advance to the next item
                i += 1
            }
        }
        return returnList
    }

    private fun geometryDepthFirstSearch(
        currNode: SemanticsNode,
        layoutIsRtl: Boolean,
        geometryList: ArrayList<SemanticsNode>,
        containerMapToChildren: MutableMap<Int, MutableList<SemanticsNode>>
    ) {
        // We only want to add children that are either traversalGroups or are
        // screen reader focusable. The child must also be in the current pruned semantics tree.
        val isTraversalGroup = currNode.isTraversalGroup
        if ((isTraversalGroup || isScreenReaderFocusable(currNode)) &&
            currNode.id in currentSemanticsNodes.keys) {
            geometryList.add(currNode)
        }
        if (isTraversalGroup) {
            // Recurse and record the container's children, sorted
            containerMapToChildren[currNode.id] = subtreeSortedByGeometryGrouping(
                layoutIsRtl, currNode.children.toMutableList()
            )
        } else {
            // Otherwise, continue adding children to the list that'll be sorted regardless of
            // hierarchy
            currNode.children.fastForEach { child ->
                geometryDepthFirstSearch(child, layoutIsRtl, geometryList, containerMapToChildren)
            }
        }
    }

    /**
     * This function prepares a subtree for `sortByGeometryGroupings` by retrieving all
     * non-container nodes and adding them to the list to be geometrically sorted. We recurse on
     * containers (if they exist) and add their sorted children to an optional mapping.
     * The list to be sorted and child mapping is passed into `sortByGeometryGroupings`.
     */
    private fun subtreeSortedByGeometryGrouping(
        layoutIsRtl: Boolean,
        listToSort: MutableList<SemanticsNode>
    ): MutableList<SemanticsNode> {
        // This should be mapping of [containerID: listOfSortedChildren], only populated if there
        // are container nodes in this level. If there are container nodes, `containerMapToChildren`
        // would look like {containerId: [sortedChild, sortedChild], containerId: [sortedChild]}
        val containerMapToChildren = mutableMapOf<Int, MutableList<SemanticsNode>>()
        val geometryList = ArrayList<SemanticsNode>()

        listToSort.fastForEach { node ->
            geometryDepthFirstSearch(node, layoutIsRtl, geometryList, containerMapToChildren)
        }

        return sortByGeometryGroupings(layoutIsRtl, geometryList, containerMapToChildren)
    }

    private fun setTraversalValues() {
        idToBeforeMap.clear()
        idToAfterMap.clear()

        val hostSemanticsNode =
            currentSemanticsNodes[AccessibilityNodeProviderCompat.HOST_VIEW_ID]
                ?.semanticsNode!!
        val layoutIsRtl = hostSemanticsNode.isRtl

        val semanticsOrderList = subtreeSortedByGeometryGrouping(
            layoutIsRtl, mutableListOf(hostSemanticsNode)
        )

        // Iterate through our ordered list, and creating a mapping of current node to next node ID
        // We'll later read through this and set traversal order with IdToBeforeMap
        for (i in 1..semanticsOrderList.lastIndex) {
            val prevId = semanticsOrderList[i - 1].id
            val currId = semanticsOrderList[i].id
            idToBeforeMap[prevId] = currId
            idToAfterMap[currId] = prevId
        }
    }

    private fun isScreenReaderFocusable(
        node: SemanticsNode
    ): Boolean {
        val isSpeakingNode = node.infoContentDescriptionOrNull != null ||
            getInfoText(node) != null || getInfoStateDescriptionOrNull(node) != null ||
            getInfoIsCheckable(node)

        return node.unmergedConfig.isMergingSemanticsOfDescendants ||
            node.isUnmergedLeafNode && isSpeakingNode
    }

    @VisibleForTesting
    @OptIn(ExperimentalComposeUiApi::class)
    fun populateAccessibilityNodeInfoProperties(
        virtualViewId: Int,
        info: AccessibilityNodeInfoCompat,
        semanticsNode: SemanticsNode
    ) {
        // set classname
        info.className = ClassName
        val role = semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.Role)
        role?.let {
            if (semanticsNode.isFake || semanticsNode.replacedChildren.isEmpty()) {
                if (role == Role.Tab) {
                    info.roleDescription = view.context.resources.getString(R.string.tab)
                } else if (role == Role.Switch) {
                    info.roleDescription = view.context.resources.getString(R.string.switch_role)
                } else {
                    val className = role.toLegacyClassName()
                    // Images are often minor children of larger widgets, so we only want to
                    // announce the Image role when the image itself is focusable.
                    if (role != Role.Image ||
                        semanticsNode.isUnmergedLeafNode ||
                        semanticsNode.unmergedConfig.isMergingSemanticsOfDescendants
                    ) {
                        info.className = className
                    }
                }
            }
        }
        if (semanticsNode.isTextField) {
            info.className = TextFieldClassName
        }
        if (semanticsNode.config.contains(SemanticsProperties.Text)) {
            info.className = TextClassName
        }

        info.packageName = view.context.packageName

        // This property exists to distinguish semantically meaningful nodes from purely structural
        // or decorative UI elements.  Most nodes are considered important, except:
        // * Non-merging nodes with only non-accessibility-speakable properties.
        //     * Of the built-in ones, the key example is testTag.
        //     * Custom SemanticsPropertyKeys defined outside the UI package
        //       are also non-speakable.
        // * Non-merging nodes that are empty: notably, clearAndSetSemantics {}
        //   and the root of the SemanticsNode tree.
        info.isImportantForAccessibility =
            semanticsNode.unmergedConfig.isMergingSemanticsOfDescendants ||
            semanticsNode.unmergedConfig.containsImportantForAccessibility()

        semanticsNode.replacedChildren.fastForEach { child ->
            if (currentSemanticsNodes.contains(child.id)) {
                val holder = view.androidViewsHandler.layoutNodeToHolder[child.layoutNode]
                if (holder != null) {
                    info.addChild(holder)
                } else {
                    info.addChild(view, child.id)
                }
            }
        }

        // Manage internal accessibility focus state.
        if (focusedVirtualViewId == virtualViewId) {
            info.isAccessibilityFocused = true
            info.addAction(AccessibilityActionCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        } else {
            info.isAccessibilityFocused = false
            info.addAction(AccessibilityActionCompat.ACTION_ACCESSIBILITY_FOCUS)
        }

        setText(semanticsNode, info)
        setContentInvalid(semanticsNode, info)
        setStateDescription(semanticsNode, info)
        setIsCheckable(semanticsNode, info)

        val toggleState = semanticsNode.unmergedConfig.getOrNull(
            SemanticsProperties.ToggleableState
        )
        toggleState?.let {
            if (toggleState == ToggleableState.On) {
                info.isChecked = true
            } else if (toggleState == ToggleableState.Off) {
                info.isChecked = false
            }
        }
        semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.Selected)?.let {
            if (role == Role.Tab) {
                // Tab in native android uses selected property
                info.isSelected = it
            } else {
                info.isChecked = it
            }
        }

        if (!semanticsNode.unmergedConfig.isMergingSemanticsOfDescendants ||
            // we don't emit fake nodes for nodes without children, therefore we should assign
            // content description for such nodes
            semanticsNode.replacedChildren.isEmpty()
        ) {
            info.contentDescription = semanticsNode.infoContentDescriptionOrNull
        }

        // Map testTag to resourceName if testTagsAsResourceId == true (which can be set by an ancestor)
        val testTag = semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.TestTag)
        if (testTag != null) {
            var testTagsAsResourceId = false
            var current: SemanticsNode? = semanticsNode
            while (current != null) {
                if (current.unmergedConfig.contains(
                        SemanticsPropertiesAndroid.TestTagsAsResourceId
                    )
                ) {
                    testTagsAsResourceId = current.unmergedConfig.get(
                        SemanticsPropertiesAndroid.TestTagsAsResourceId
                    )
                    break
                } else {
                    current = current.parent
                }
            }

            if (testTagsAsResourceId) {
                info.viewIdResourceName = testTag
            }
        }

        semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.Heading)?.let {
            info.isHeading = true
        }
        info.isPassword = semanticsNode.isPassword
        // Note editable is not added to semantics properties api.
        info.isEditable = semanticsNode.isTextField
        info.isEnabled = semanticsNode.enabled()
        info.isFocusable = semanticsNode.unmergedConfig.contains(SemanticsProperties.Focused)
        if (info.isFocusable) {
            info.isFocused = semanticsNode.unmergedConfig[SemanticsProperties.Focused]
            if (info.isFocused) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS)
            } else {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_FOCUS)
            }
        }

        // Mark invisible nodes
        info.isVisibleToUser = semanticsNode.isVisible

        semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.LiveRegion)?.let {
            info.liveRegion = when (it) {
                LiveRegionMode.Polite -> ACCESSIBILITY_LIVE_REGION_POLITE
                LiveRegionMode.Assertive -> ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                else -> ACCESSIBILITY_LIVE_REGION_POLITE
            }
        }
        info.isClickable = false
        semanticsNode.unmergedConfig.getOrNull(SemanticsActions.OnClick)?.let {
            // Selectable items that are already selected should not announce it again
            val isSelected =
                semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.Selected) == true
            info.isClickable = !isSelected
            if (semanticsNode.enabled() && !isSelected) {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        it.label
                    )
                )
            }
        }
        info.isLongClickable = false
        semanticsNode.unmergedConfig.getOrNull(SemanticsActions.OnLongClick)?.let {
            info.isLongClickable = true
            if (semanticsNode.enabled()) {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                        it.label
                    )
                )
            }
        }

        // The config will contain this action only if there is a text selection at the moment.
        semanticsNode.unmergedConfig.getOrNull(SemanticsActions.CopyText)?.let {
            info.addAction(
                AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_COPY,
                    it.label
                )
            )
        }
        if (semanticsNode.enabled()) {
            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.SetText)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_SET_TEXT,
                        it.label
                    )
                )
            }

            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.OnImeAction)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        android.R.id.accessibilityActionImeEnter,
                        it.label
                    )
                )
            }

            // The config will contain this action only if there is a text selection at the moment.
            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.CutText)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CUT,
                        it.label
                    )
                )
            }

            // The config will contain the action anyway, therefore we check the clipboard text to
            // decide whether to add the action to the node or not.
            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.PasteText)?.let {
                if (info.isFocused && view.clipboardManager.hasText()) {
                    info.addAction(
                        AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_PASTE,
                            it.label
                        )
                    )
                }
            }
        }

        val text = getIterableTextForAccessibility(semanticsNode)
        if (!text.isNullOrEmpty()) {
            info.setTextSelection(
                getAccessibilitySelectionStart(semanticsNode),
                getAccessibilitySelectionEnd(semanticsNode)
            )
            val setSelectionAction =
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.SetSelection)
            // ACTION_SET_SELECTION should be provided even when SemanticsActions.SetSelection
            // semantics action is not provided by the component
            info.addAction(
                AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
                    setSelectionAction?.label
                )
            )
            info.addAction(AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
            info.addAction(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
            info.movementGranularities =
                AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER or
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD or
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH
            // We only traverse the text when contentDescription is not set.
            val contentDescription = semanticsNode.unmergedConfig.getOrNull(
                SemanticsProperties.ContentDescription
            )
            if (contentDescription.isNullOrEmpty() &&
                semanticsNode.unmergedConfig.contains(SemanticsActions.GetTextLayoutResult) &&
                // Talkback does not handle below granularities for text field (which includes
                // label/hint) when text field is not in focus
                !semanticsNode.excludeLineAndPageGranularities()
            ) {
                info.movementGranularities = info.movementGranularities or
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE or
                    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val extraDataKeys: MutableList<String> = mutableListOf()
            extraDataKeys.add(ExtraDataIdKey)
            if (!info.text.isNullOrEmpty() &&
                semanticsNode.unmergedConfig.contains(SemanticsActions.GetTextLayoutResult)
            ) {
                extraDataKeys.add(EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
            }
            if (semanticsNode.unmergedConfig.contains(SemanticsProperties.TestTag)) {
                extraDataKeys.add(ExtraDataTestTagKey)
            }

            AccessibilityNodeInfoVerificationHelperMethods.setAvailableExtraData(
                info.unwrap(),
                extraDataKeys
            )
        }

        val rangeInfo =
            semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        if (rangeInfo != null) {
            if (semanticsNode.unmergedConfig.contains(SemanticsActions.SetProgress)) {
                info.className = "android.widget.SeekBar"
            } else {
                info.className = "android.widget.ProgressBar"
            }
            if (rangeInfo !== ProgressBarRangeInfo.Indeterminate) {
                info.rangeInfo = AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                    AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
                    rangeInfo.range.start,
                    rangeInfo.range.endInclusive,
                    rangeInfo.current
                )
            }
            if (semanticsNode.unmergedConfig.contains(SemanticsActions.SetProgress) &&
                semanticsNode.enabled()
            ) {
                if (rangeInfo.current <
                    rangeInfo.range.endInclusive.coerceAtLeast(rangeInfo.range.start)
                ) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
                }
                if (rangeInfo.current >
                    rangeInfo.range.start.coerceAtMost(rangeInfo.range.endInclusive)
                ) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Api24Impl.addSetProgressAction(info, semanticsNode)
        }

        setCollectionInfo(semanticsNode, info)
        setCollectionItemInfo(semanticsNode, info)

        // Will the scrollable scroll when ACTION_SCROLL_FORWARD is performed?
        fun ScrollAxisRange.canScrollForward(): Boolean {
            return value() < maxValue() && !reverseScrolling || value() > 0f && reverseScrolling
        }

        // Will the scrollable scroll when ACTION_SCROLL_BACKWARD is performed?
        fun ScrollAxisRange.canScrollBackward(): Boolean {
            return value() > 0f && !reverseScrolling || value() < maxValue() && reverseScrolling
        }

        val xScrollState =
            semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val scrollAction = semanticsNode.unmergedConfig.getOrNull(SemanticsActions.ScrollBy)
        if (xScrollState != null && scrollAction != null) {
            // Talkback defines SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION, so we need to
            // assign a role for auto scroll to work. Node with collectionInfo resolved by
            // Talkback to ROLE_LIST and supports autoscroll too
            if (!semanticsNode.hasCollectionInfo()) {
                info.className = "android.widget.HorizontalScrollView"
            }
            if (xScrollState.maxValue() > 0f) {
                info.isScrollable = true
            }
            if (semanticsNode.enabled()) {
                if (xScrollState.canScrollForward()) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
                    info.addAction(
                        if (!semanticsNode.isRtl) {
                            AccessibilityActionCompat.ACTION_SCROLL_RIGHT
                        } else {
                            AccessibilityActionCompat.ACTION_SCROLL_LEFT
                        }
                    )
                }
                if (xScrollState.canScrollBackward()) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
                    info.addAction(
                        if (!semanticsNode.isRtl) {
                            AccessibilityActionCompat.ACTION_SCROLL_LEFT
                        } else {
                            AccessibilityActionCompat.ACTION_SCROLL_RIGHT
                        }
                    )
                }
            }
        }
        val yScrollState =
            semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        if (yScrollState != null && scrollAction != null) {
            // Talkback defines SCROLLABLE_ROLE_FILTER_FOR_DIRECTION_NAVIGATION, so we need to
            // assign a role for auto scroll to work. Node with collectionInfo resolved by
            // Talkback to ROLE_LIST and supports autoscroll too
            if (!semanticsNode.hasCollectionInfo()) {
                info.className = "android.widget.ScrollView"
            }
            if (yScrollState.maxValue() > 0f) {
                info.isScrollable = true
            }
            if (semanticsNode.enabled()) {
                if (yScrollState.canScrollForward()) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_DOWN)
                }
                if (yScrollState.canScrollBackward()) {
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
                    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_UP)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29Impl.addPageActions(info, semanticsNode)
        }

        info.paneTitle = semanticsNode.unmergedConfig.getOrNull(SemanticsProperties.PaneTitle)

        if (semanticsNode.enabled()) {
            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.Expand)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_EXPAND,
                        it.label
                    )
                )
            }

            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.Collapse)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_COLLAPSE,
                        it.label
                    )
                )
            }

            semanticsNode.unmergedConfig.getOrNull(SemanticsActions.Dismiss)?.let {
                info.addAction(
                    AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_DISMISS,
                        it.label
                    )
                )
            }

            if (semanticsNode.unmergedConfig.contains(CustomActions)) {
                val customActions = semanticsNode.unmergedConfig[CustomActions]
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
                    customActions.fastForEach { action ->
                        if (oldLabelToActionId!!.contains(action.label)) {
                            val actionId = oldLabelToActionId[action.label]
                            currentActionIdToLabel.put(actionId!!, action.label)
                            currentLabelToActionId[action.label] = actionId
                            availableIds.remove(actionId)
                            info.addAction(AccessibilityActionCompat(actionId, action.label))
                        } else {
                            unassignedActions.add(action)
                        }
                    }
                    unassignedActions.fastForEachIndexed { index, action ->
                        val actionId = availableIds[index]
                        currentActionIdToLabel.put(actionId, action.label)
                        currentLabelToActionId[action.label] = actionId
                        info.addAction(AccessibilityActionCompat(actionId, action.label))
                    }
                } else {
                    customActions.fastForEachIndexed { index, action ->
                        val actionId = AccessibilityActionsResourceIds[index]
                        currentActionIdToLabel.put(actionId, action.label)
                        currentLabelToActionId[action.label] = actionId
                        info.addAction(AccessibilityActionCompat(actionId, action.label))
                    }
                }
                actionIdToLabel.put(virtualViewId, currentActionIdToLabel)
                labelToActionId.put(virtualViewId, currentLabelToActionId)
            }
        }

        info.isScreenReaderFocusable = isScreenReaderFocusable(semanticsNode)

        // `beforeId` refers to the semanticsId that should be read before this `virtualViewId`.
        val beforeId = idToBeforeMap[virtualViewId]
        beforeId?.let {
            val beforeView = view.androidViewsHandler.semanticsIdToView(beforeId)
            if (beforeView != null) {
                // If the node that should come before this one is a view, we want to pass in the
                // "before" view itself, which is retrieved from our `idToViewMap`.
                info.setTraversalBefore(beforeView)
            } else {
                // Otherwise, we'll set the "before" value by passing in the semanticsId.
                info.setTraversalBefore(view, beforeId)
            }
            addExtraDataToAccessibilityNodeInfoHelper(
                virtualViewId, info.unwrap(), EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL, null
            )
        }

        val afterId = idToAfterMap[virtualViewId]
        afterId?.let {
            val afterView = view.androidViewsHandler.semanticsIdToView(afterId)
            // Specially use `traversalAfter` value if the node after is a View,
            // as expressing the order using traversalBefore in this case would require mutating the
            // View itself, which is not under Compose's full control.
            if (afterView != null) {
                info.setTraversalAfter(afterView)
                addExtraDataToAccessibilityNodeInfoHelper(
                    virtualViewId, info.unwrap(), EXTRA_DATA_TEST_TRAVERSALAFTER_VAL, null
                )
            }
        }
    }

    /** Set the error text for this node */
    private fun setContentInvalid(node: SemanticsNode, info: AccessibilityNodeInfoCompat) {
        if (node.unmergedConfig.contains(SemanticsProperties.Error)) {
            info.isContentInvalid = true
            info.error = node.unmergedConfig.getOrNull(SemanticsProperties.Error)
        }
    }

    private fun getInfoStateDescriptionOrNull(
        node: SemanticsNode
    ): String? {
        var stateDescription = node.unmergedConfig.getOrNull(SemanticsProperties.StateDescription)
        val toggleState = node.unmergedConfig.getOrNull(SemanticsProperties.ToggleableState)
        val role = node.unmergedConfig.getOrNull(SemanticsProperties.Role)

        // Check toggle state and retrieve description accordingly
        toggleState?.let {
            when (it) {
                ToggleableState.On -> {
                    // Unfortunately, talkback has a bug of using "checked", so we set state
                    // description here
                    if (role == Role.Switch && stateDescription == null) {
                        stateDescription = view.context.resources.getString(R.string.on)
                    }
                }

                ToggleableState.Off -> {
                    // Unfortunately, talkback has a bug of using "not checked", so we set state
                    // description here
                    if (role == Role.Switch && stateDescription == null) {
                        stateDescription = view.context.resources.getString(R.string.off)
                    }
                }

                ToggleableState.Indeterminate -> {
                    if (stateDescription == null) {
                        stateDescription =
                            view.context.resources.getString(R.string.indeterminate)
                    }
                }
            }
        }

        // Check Selected property and retrieve description accordingly
        node.unmergedConfig.getOrNull(SemanticsProperties.Selected)?.let {
            if (role != Role.Tab) {
                if (stateDescription == null) {
                    // If a radio entry (radio button + text) is selectable, it won't have the role
                    // RadioButton, so if we use info.isCheckable/info.isChecked, talkback will say
                    // "checked/not checked" instead "selected/note selected".
                    stateDescription = if (it) {
                        view.context.resources.getString(R.string.selected)
                    } else {
                        view.context.resources.getString(R.string.not_selected)
                    }
                }
            }
        }

        // Check if a node has progress bar range info and retrieve description accordingly
        val rangeInfo =
            node.unmergedConfig.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        rangeInfo?.let {
            // let's set state description here and use state description change events.
            // otherwise, we need to send out type_view_selected event, as the old android
            // versions do. But the support for type_view_selected event for progress bars
            // maybe deprecated in talkback in the future.
            if (rangeInfo !== ProgressBarRangeInfo.Indeterminate) {
                if (stateDescription == null) {
                    val valueRange = rangeInfo.range
                    val progress = (
                        if (valueRange.endInclusive - valueRange.start == 0f) 0f
                        else (rangeInfo.current - valueRange.start) /
                            (valueRange.endInclusive - valueRange.start)
                        ).coerceIn(0f, 1f)

                    // We only display 0% or 100% when it is exactly 0% or 100%.
                    val percent = when (progress) {
                        0f -> 0
                        1f -> 100
                        else -> (progress * 100).roundToInt().coerceIn(1, 99)
                    }
                    stateDescription =
                        view.context.resources.getString(R.string.template_percent, percent)
                }
            } else if (stateDescription == null) {
                stateDescription = view.context.resources.getString(R.string.in_progress)
            }
        }

        return stateDescription
    }

    private fun setStateDescription(
        node: SemanticsNode,
        info: AccessibilityNodeInfoCompat,
    ) {
        info.stateDescription = getInfoStateDescriptionOrNull(node)
    }

    private fun getInfoIsCheckable(
        node: SemanticsNode
    ): Boolean {
        var isCheckable = false
        val toggleState = node.unmergedConfig.getOrNull(SemanticsProperties.ToggleableState)
        val role = node.unmergedConfig.getOrNull(SemanticsProperties.Role)

        toggleState?.let {
            isCheckable = true
        }

        node.unmergedConfig.getOrNull(SemanticsProperties.Selected)?.let {
            if (role != Role.Tab) {
                isCheckable = true
            }
        }

        return isCheckable
    }

    private fun setIsCheckable(
        node: SemanticsNode,
        info: AccessibilityNodeInfoCompat
    ) {
        info.isCheckable = getInfoIsCheckable(node)
    }

    // This needs to be here instead of around line 3000 because we need access to the `view`
    // that is inside the `AndroidComposeViewAccessibilityDelegateCompat` class
    @OptIn(InternalTextApi::class)
    private fun getInfoText(
        node: SemanticsNode
    ): SpannableString? {
        val fontFamilyResolver: FontFamily.Resolver = view.fontFamilyResolver
        val editableTextToAssign = trimToSize(
            node.unmergedConfig.getTextForTextField()
                ?.toAccessibilitySpannableString(
                    density = view.density,
                    fontFamilyResolver,
                    urlSpanCache
                ),
            ParcelSafeTextLength
        )

        val textToAssign = trimToSize(
            node.unmergedConfig.getOrNull(SemanticsProperties.Text)?.firstOrNull()
                ?.toAccessibilitySpannableString(
                    density = view.density,
                    fontFamilyResolver,
                    urlSpanCache
                ),
            ParcelSafeTextLength
        )
        return editableTextToAssign ?: textToAssign
    }

    private fun setText(
        node: SemanticsNode,
        info: AccessibilityNodeInfoCompat,
    ) {
        info.text = getInfoText(node)
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
        if (!isTouchExplorationEnabled) {
            return false
        }
        // TODO: Check virtual view visibility.
        if (!isAccessibilityFocused(virtualViewId)) {
            // Clear focus from the previously focused view, if applicable.
            if (focusedVirtualViewId != InvalidId) {
                sendEventForVirtualView(
                    focusedVirtualViewId,
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                )
            }

            // Set focus on the new view.
            focusedVirtualViewId = virtualViewId

            view.invalidate()
            sendEventForVirtualView(
                virtualViewId,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
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
        contentDescription: List<String>? = null
    ): Boolean {
        if (virtualViewId == InvalidId || !isEnabled) {
            return false
        }

        val event: AccessibilityEvent = createEvent(virtualViewId, eventType)
        if (contentChangeType != null) {
            event.contentChangeTypes = contentChangeType
        }
        if (contentDescription != null) {
            event.contentDescription = contentDescription.fastJoinToString(",")
        }

        return sendEvent(event)
    }

    /**
     * Send an accessibility event.
     *
     * @param event The accessibility event to send.
     * @return true if the event was sent successfully.
     */
    private fun sendEvent(event: AccessibilityEvent): Boolean {
        // only send an event if there's an enabled service listening for events of this type
        if (!isEnabledForAccessibility) {
            return false
        }

        return view.parent.requestSendAccessibilityEvent(view, event)
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
    @Suppress("DEPRECATION")
    @VisibleForTesting
    internal fun createEvent(virtualViewId: Int, eventType: Int): AccessibilityEvent {
        val event: AccessibilityEvent = AccessibilityEvent.obtain(eventType)
        event.isEnabled = true
        event.className = ClassName

        // Don't allow the client to override these properties.
        event.packageName = view.context.packageName
        event.setSource(view, virtualViewId)

        if (isEnabledForAccessibility) {
            // populate additional information from the node
            currentSemanticsNodes[virtualViewId]?.let {
                event.isPassword = it.semanticsNode.isPassword
            }
        }

        return event
    }

    private fun createTextSelectionChangedEvent(
        virtualViewId: Int,
        fromIndex: Int?,
        toIndex: Int?,
        itemCount: Int?,
        text: CharSequence?
    ): AccessibilityEvent {
        return createEvent(
            virtualViewId,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ).apply {
            fromIndex?.let { this.fromIndex = it }
            toIndex?.let { this.toIndex = it }
            itemCount?.let { this.itemCount = it }
            text?.let { this.text.add(it) }
        }
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
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            )
            return true
        }
        return false
    }

    private fun performActionHelper(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        val node = currentSemanticsNodes[virtualViewId]?.semanticsNode ?: return false

        // Actions can be performed when disabled.
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS ->
                return requestAccessibilityFocus(virtualViewId)

            AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS ->
                return clearAccessibilityFocus(virtualViewId)

            AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
            AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> {
                if (arguments != null) {
                    val granularity = arguments.getInt(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT
                    )
                    val extendSelection = arguments.getBoolean(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN
                    )
                    return traverseAtGranularity(
                        node, granularity,
                        action == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
                        extendSelection
                    )
                }
                return false
            }

            AccessibilityNodeInfoCompat.ACTION_SET_SELECTION -> {
                val start = arguments?.getInt(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, -1
                ) ?: -1
                val end = arguments?.getInt(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, -1
                ) ?: -1
                // Note: This is a little different from current android framework implementation.
                val success = setAccessibilitySelection(node, start, end, false)
                // Text selection changed event already updates the cache. so this may not be
                // necessary.
                if (success) {
                    sendEventForVirtualView(
                        semanticsNodeIdToAccessibilityVirtualNodeId(node.id),
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
                    )
                }
                return success
            }

            AccessibilityNodeInfoCompat.ACTION_COPY -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.CopyText
                )?.action?.invoke() ?: false
            }
        }

        if (!node.enabled()) {
            return false
        }

        // Actions can't be performed when disabled.
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                val result =
                    node.unmergedConfig.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                return result ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                return node.unmergedConfig.getOrNull(SemanticsActions.OnLongClick)?.action?.invoke()
                    ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
            android.R.id.accessibilityActionScrollDown,
            android.R.id.accessibilityActionScrollUp,
            android.R.id.accessibilityActionScrollRight,
            android.R.id.accessibilityActionScrollLeft -> {
                // Introduce a few shorthands:
                val scrollForward = action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD
                val scrollBackward = action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
                val scrollLeft = action == android.R.id.accessibilityActionScrollLeft
                val scrollRight = action == android.R.id.accessibilityActionScrollRight
                val scrollUp = action == android.R.id.accessibilityActionScrollUp
                val scrollDown = action == android.R.id.accessibilityActionScrollDown

                val scrollHorizontal = scrollLeft || scrollRight || scrollForward || scrollBackward
                val scrollVertical = scrollUp || scrollDown || scrollForward || scrollBackward

                if (scrollForward || scrollBackward) {
                    val rangeInfo =
                        node.unmergedConfig.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
                    val setProgressAction =
                        node.unmergedConfig.getOrNull(SemanticsActions.SetProgress)
                    if (rangeInfo != null && setProgressAction != null) {
                        val max = rangeInfo.range.endInclusive.coerceAtLeast(rangeInfo.range.start)
                        val min = rangeInfo.range.start.coerceAtMost(rangeInfo.range.endInclusive)
                        var increment = if (rangeInfo.steps > 0) {
                            (max - min) / (rangeInfo.steps + 1)
                        } else {
                            (max - min) / AccessibilitySliderStepsCount
                        }
                        if (scrollBackward) {
                            increment = -increment
                        }
                        return setProgressAction.action?.invoke(rangeInfo.current + increment)
                            ?: false
                    }
                }

                // Will the scrollable scroll when ScrollBy is invoked with the given [amount]?
                fun ScrollAxisRange.canScroll(amount: Float): Boolean {
                    return amount < 0 && value() > 0 || amount > 0 && value() < maxValue()
                }

                val viewport = node.layoutInfo.coordinates.boundsInParent().size

                // The lint warning text is unstable because anonymous lambdas have an autogenerated
                // name, so suppress this lint warning with @SuppressLint instead of a baseline.
                @SuppressLint("PrimitiveInLambda")
                val scrollAction =
                    node.unmergedConfig.getOrNull(SemanticsActions.ScrollBy) ?: return false

                val xScrollState =
                    node.unmergedConfig.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                if (xScrollState != null && scrollHorizontal) {
                    var amountToScroll = viewport.width
                    if (scrollLeft || scrollBackward) {
                        amountToScroll = -amountToScroll
                    }
                    if (xScrollState.reverseScrolling) {
                        amountToScroll = -amountToScroll
                    }
                    if (node.isRtl && (scrollLeft || scrollRight)) {
                        amountToScroll = -amountToScroll
                    }
                    if (xScrollState.canScroll(amountToScroll)) {
                        return scrollAction.action?.invoke(amountToScroll, 0f) ?: false
                    }
                }

                val yScrollState =
                    node.unmergedConfig.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                if (yScrollState != null && scrollVertical) {
                    var amountToScroll = viewport.height
                    if (scrollUp || scrollBackward) {
                        amountToScroll = -amountToScroll
                    }
                    if (yScrollState.reverseScrolling) {
                        amountToScroll = -amountToScroll
                    }
                    if (yScrollState.canScroll(amountToScroll)) {
                        return scrollAction.action?.invoke(0f, amountToScroll) ?: false
                    }
                }

                return false
            }

            android.R.id.accessibilityActionPageUp -> {
                val pageAction = node.unmergedConfig.getOrNull(SemanticsActions.PageUp)
                return pageAction?.action?.invoke() ?: false
            }

            android.R.id.accessibilityActionPageDown -> {
                val pageAction = node.unmergedConfig.getOrNull(SemanticsActions.PageDown)
                return pageAction?.action?.invoke() ?: false
            }

            android.R.id.accessibilityActionPageLeft -> {
                val pageAction = node.unmergedConfig.getOrNull(SemanticsActions.PageLeft)
                return pageAction?.action?.invoke() ?: false
            }

            android.R.id.accessibilityActionPageRight -> {
                val pageAction = node.unmergedConfig.getOrNull(SemanticsActions.PageRight)
                return pageAction?.action?.invoke() ?: false
            }

            android.R.id.accessibilityActionSetProgress -> {
                if (arguments == null || !arguments.containsKey(
                        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE
                    )
                ) {
                    return false
                }
                return node.unmergedConfig.getOrNull(SemanticsActions.SetProgress)?.action?.invoke(
                    arguments.getFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE)
                ) ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_FOCUS -> {
                return node.unmergedConfig.getOrNull(SemanticsActions.RequestFocus)
                    ?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_CLEAR_FOCUS -> {
                return if (node.unmergedConfig.getOrNull(SemanticsProperties.Focused) == true) {
                    view.focusOwner.clearFocus()
                    true
                } else {
                    false
                }
            }

            AccessibilityNodeInfoCompat.ACTION_SET_TEXT -> {
                val text = arguments?.getString(
                    AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE
                )
                return node.unmergedConfig.getOrNull(SemanticsActions.SetText)
                    ?.action?.invoke(AnnotatedString(text ?: "")) ?: false
            }

            android.R.id.accessibilityActionImeEnter -> {
                return node.unmergedConfig.getOrNull(SemanticsActions.OnImeAction)
                    ?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_PASTE -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.PasteText
                )?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_CUT -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.CutText
                )?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_EXPAND -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.Expand
                )?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_COLLAPSE -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.Collapse
                )?.action?.invoke() ?: false
            }

            AccessibilityNodeInfoCompat.ACTION_DISMISS -> {
                return node.unmergedConfig.getOrNull(
                    SemanticsActions.Dismiss
                )?.action?.invoke() ?: false
            }

            android.R.id.accessibilityActionShowOnScreen -> {
                // TODO(b/190865803): Consider scrolling nested containers instead of only the first one.
                var scrollableAncestor: SemanticsNode? = node.parent
                var scrollAction = scrollableAncestor?.config?.getOrNull(SemanticsActions.ScrollBy)
                while (scrollableAncestor != null) {
                    if (scrollAction != null) {
                        break
                    }
                    scrollableAncestor = scrollableAncestor.parent
                    scrollAction = scrollableAncestor?.config?.getOrNull(SemanticsActions.ScrollBy)
                }
                if (scrollableAncestor == null) {
                    return false
                }

                // TalkBack expects the minimum amount of movement to fully reveal the node.
                // First, get the viewport and the target bounds in root coordinates
                val viewportInParent = scrollableAncestor.layoutInfo.coordinates.boundsInParent()
                val parentInRoot = scrollableAncestor.layoutInfo.coordinates
                    .parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero
                val viewport = viewportInParent.translate(parentInRoot)
                val target = Rect(node.positionInRoot, node.size.toSize())

                val xScrollState = scrollableAncestor.unmergedConfig
                    .getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                val yScrollState = scrollableAncestor.unmergedConfig
                    .getOrNull(SemanticsProperties.VerticalScrollAxisRange)

                // Given the desired scroll value to align either side of the target with the
                // viewport, what delta should we go with?
                // If we need to scroll in opposite directions for both sides, don't scroll at all.
                // Otherwise, take the delta that scrolls the least amount.
                fun scrollDelta(a: Float, b: Float): Float =
                    if (sign(a) == sign(b)) if (abs(a) < abs(b)) a else b else 0f

                // Get the desired delta X
                var dx = scrollDelta(target.left - viewport.left, target.right - viewport.right)
                // And adjust for reversing properties
                if (xScrollState?.reverseScrolling == true) dx = -dx
                if (node.isRtl) dx = -dx

                // Get the desired delta Y
                var dy = scrollDelta(target.top - viewport.top, target.bottom - viewport.bottom)
                // And adjust for reversing properties
                if (yScrollState?.reverseScrolling == true) dy = -dy

                return scrollAction?.action?.invoke(dx, dy) ?: false
            }
            // TODO: handling for other system actions
            else -> {
                val label = actionIdToLabel[virtualViewId]?.get(action) ?: return false
                val customActions = node.unmergedConfig.getOrNull(CustomActions) ?: return false
                customActions.fastForEach { customAction ->
                    if (customAction.label == label) {
                        return customAction.action()
                    }
                }
                return false
            }
        }
    }

    private fun addExtraDataToAccessibilityNodeInfoHelper(
        virtualViewId: Int,
        info: AccessibilityNodeInfo,
        extraDataKey: String,
        arguments: Bundle?
    ) {
        val node = currentSemanticsNodes[virtualViewId]?.semanticsNode ?: return
        val text = getIterableTextForAccessibility(node)

        // This extra is just for testing: needed a way to retrieve `traversalBefore` and
        // `traversalAfter` from a non-sealed instance of an ANI
        if (extraDataKey == EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL) {
            idToBeforeMap[virtualViewId]?.let {
                info.extras.putInt(extraDataKey, it)
            }
        } else if (extraDataKey == EXTRA_DATA_TEST_TRAVERSALAFTER_VAL) {
            idToAfterMap[virtualViewId]?.let {
                info.extras.putInt(extraDataKey, it)
            }
        } else if (node.unmergedConfig.contains(SemanticsActions.GetTextLayoutResult) &&
            arguments != null && extraDataKey == EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        ) {
            val positionInfoStartIndex = arguments.getInt(
                EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, -1
            )
            val positionInfoLength = arguments.getInt(
                EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, -1
            )
            if ((positionInfoLength <= 0) || (positionInfoStartIndex < 0) ||
                (positionInfoStartIndex >= text?.length ?: Int.MAX_VALUE)
            ) {
                Log.e(LogTag, "Invalid arguments for accessibility character locations")
                return
            }
            val textLayoutResult = getTextLayoutResult(node.unmergedConfig) ?: return
            val boundingRects = mutableListOf<RectF?>()
            for (i in 0 until positionInfoLength) {
                // This is a workaround until we fix the merging issue in b/157474582.
                if (positionInfoStartIndex + i >= textLayoutResult.layoutInput.text.length) {
                    boundingRects.add(null)
                    continue
                }
                val bounds = textLayoutResult.getBoundingBox(positionInfoStartIndex + i)
                val boundsOnScreen = toScreenCoords(node, bounds)
                boundingRects.add(boundsOnScreen)
            }
            info.extras.putParcelableArray(extraDataKey, boundingRects.toTypedArray())
        } else if (node.unmergedConfig.contains(SemanticsProperties.TestTag) &&
            arguments != null && extraDataKey == ExtraDataTestTagKey
        ) {
            val testTag = node.unmergedConfig.getOrNull(SemanticsProperties.TestTag)
            if (testTag != null) {
                info.extras.putCharSequence(extraDataKey, testTag)
            }
        } else if (extraDataKey == ExtraDataIdKey) {
            info.extras.putInt(extraDataKey, node.id)
        }
    }

    private fun toScreenCoords(textNode: SemanticsNode?, bounds: Rect): RectF? {
        if (textNode == null) return null
        val boundsInRoot = bounds.translate(textNode.positionInRoot)
        val textNodeBoundsInRoot = textNode.boundsInRoot

        // Only visible or partially visible locations are used.
        val visibleBounds = if (boundsInRoot.overlaps(textNodeBoundsInRoot)) {
            boundsInRoot.intersect(textNodeBoundsInRoot)
        } else {
            null
        }

        return if (visibleBounds != null) {
            val topLeftInScreen =
                view.localToScreen(Offset(visibleBounds.left, visibleBounds.top))
            val bottomRightInScreen =
                view.localToScreen(Offset(visibleBounds.right, visibleBounds.bottom))
            RectF(
                topLeftInScreen.x,
                topLeftInScreen.y,
                bottomRightInScreen.x,
                bottomRightInScreen.y
            )
        } else {
            null
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
        if (!isTouchExplorationEnabled) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                val virtualViewId = hitTestSemanticsAt(event.x, event.y)
                // The android views could be view groups, so the event must be dispatched to the
                // views. Android ViewGroup.java will take care of synthesizing hover enter/exit
                // actions from hover moves.
                // Note that this should be before calling "updateHoveredVirtualView" so that in
                // the corner case of overlapped nodes, the final hover enter event is sent from
                // the node/view that we want to focus.
                val handled = view.androidViewsHandler.dispatchGenericMotionEvent(event)
                updateHoveredVirtualView(virtualViewId)
                return if (virtualViewId == InvalidId) handled else true
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                return when {
                    hoveredVirtualViewId != InvalidId -> {
                        updateHoveredVirtualView(InvalidId)
                        true
                    }

                    else -> {
                        view.androidViewsHandler.dispatchGenericMotionEvent(event)
                    }
                }
            }

            else -> {
                return false
            }
        }
    }

    /**
     * Hit test the layout tree for semantics wrappers.
     * The return value is a virtual view id, or InvalidId if an embedded Android View was hit.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @VisibleForTesting
    internal fun hitTestSemanticsAt(x: Float, y: Float): Int {
        view.measureAndLayout()

        val hitSemanticsEntities = HitTestResult()
        view.root.hitTestSemantics(
            pointerPosition = Offset(x, y),
            hitSemanticsEntities = hitSemanticsEntities
        )

        val layoutNode = hitSemanticsEntities.lastOrNull()?.requireLayoutNode()

        var virtualViewId = InvalidId
        if (layoutNode?.nodes?.has(Nodes.Semantics) == true) {

            // The node below is not added to the tree; it's a wrapper around outer semantics to
            // use the methods available to the SemanticsNode
            val semanticsNode = SemanticsNode(layoutNode, false)

            // Do not 'find' invisible nodes when exploring by touch. This will prevent us from
            // sending events for invisible nodes
            if (semanticsNode.isVisible) {
                val androidView = view
                    .androidViewsHandler
                    .layoutNodeToHolder[layoutNode]
                if (androidView == null) {
                    virtualViewId = semanticsNodeIdToAccessibilityVirtualNodeId(
                        layoutNode.semanticsId
                    )
                }
            }
        }
        return virtualViewId
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
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        sendEventForVirtualView(previousVirtualViewId, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProviderCompat {
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
        require(size > 0) { "size should be greater than 0" }
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
        view.measureAndLayout()
        checkForSemanticsChanges()
        checkingForSemanticsChanges = false
    }

    internal fun onSemanticsChange() {
        // When accessibility is turned off, we still want to keep
        // currentSemanticsNodesInvalidated up to date so that when accessibility is turned on
        // later, we can refresh currentSemanticsNodes if currentSemanticsNodes is stale.
        currentSemanticsNodesInvalidated = true

        if (isEnabled && !checkingForSemanticsChanges) {
            checkingForSemanticsChanges = true
            handler.post(semanticsChangeChecker)
        }
    }

    /**
     * This suspend function loops for the entire lifetime of the Compose instance: it consumes
     * recent layout changes and sends events to the accessibility and content capture framework in
     * batches separated by a 100ms delay.
     */
    suspend fun boundsUpdatesEventLoop() {
        try {
            val subtreeChangedSemanticsNodesIds = ArraySet<Int>()
            for (notification in boundsUpdateChannel) {
                if (isEnabledForContentCapture) {
                    notifyContentCaptureChanges()
                }
                if (isEnabledForAccessibility) {
                    for (i in subtreeChangedLayoutNodes.indices) {
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        sendSubtreeChangeAccessibilityEvents(
                            subtreeChangedLayoutNodes.valueAt(i)!!,
                            subtreeChangedSemanticsNodesIds
                        )
                    }
                    subtreeChangedSemanticsNodesIds.clear()
                    // When the bounds of layout nodes change, we will not always get semantics
                    // change notifications because bounds is not part of semantics. And bounds
                    // change from a layout node without semantics will affect the global bounds
                    // of it children which has semantics. Bounds change will affect which nodes
                    // are covered and which nodes are not, so the currentSemanticsNodes is not
                    // up to date anymore.
                    // After the subtree events are sent, accessibility services will get the
                    // current visible/invisible state. We also try to do semantics tree diffing
                    // to send out the proper accessibility events and update our copy here so that
                    // our incremental changes (represented by accessibility events) are consistent
                    // with accessibility services. That is: change - notify - new change -
                    // notify, if we don't do the tree diffing and update our copy here, we will
                    // combine old change and new change, which is missing finer-grained
                    // notification.
                    if (!checkingForSemanticsChanges) {
                        checkingForSemanticsChanges = true
                        handler.post(semanticsChangeChecker)
                    }
                }
                subtreeChangedLayoutNodes.clear()
                delay(SendRecurringAccessibilityEventsIntervalMillis)
            }
        } finally {
            subtreeChangedLayoutNodes.clear()
        }
    }

    internal fun onLayoutChange(layoutNode: LayoutNode) {
        // When accessibility is turned off, we still want to keep
        // currentSemanticsNodesInvalidated up to date so that when accessibility is turned on
        // later, we can refresh currentSemanticsNodes if currentSemanticsNodes is stale.
        currentSemanticsNodesInvalidated = true
        if (!isEnabled) {
            return
        }
        // The layout change of a LayoutNode will also affect its children, so even if it doesn't
        // have semantics attached, we should process it.
        notifySubtreeAccessibilityStateChangedIfNeeded(layoutNode)
    }

    private fun notifySubtreeAccessibilityStateChangedIfNeeded(layoutNode: LayoutNode) {
        if (subtreeChangedLayoutNodes.add(layoutNode)) {
            boundsUpdateChannel.trySend(Unit)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun sendSubtreeChangeAccessibilityEvents(
        layoutNode: LayoutNode,
        subtreeChangedSemanticsNodesIds: ArraySet<Int>
    ) {
        // The node may be no longer available while we were waiting so check
        // again.
        if (!layoutNode.isAttached) {
            return
        }
        // Android Views will send proper events themselves.
        if (view.androidViewsHandler.layoutNodeToHolder.contains(layoutNode)) {
            return
        }
        // When we finally send the event, make sure it is an accessibility-focusable node.
        var semanticsNode = if (layoutNode.nodes.has(Nodes.Semantics))
                layoutNode
            else
                layoutNode.findClosestParentNode { it.nodes.has(Nodes.Semantics) }

        val config = semanticsNode?.collapsedSemantics ?: return
        if (!config.isMergingSemanticsOfDescendants) {
            semanticsNode.findClosestParentNode {
                it.collapsedSemantics?.isMergingSemanticsOfDescendants == true
            }?.let { semanticsNode = it }
        }
        val id = semanticsNode?.semanticsId ?: return
        if (!subtreeChangedSemanticsNodesIds.add(id)) {
            return
        }

        sendEventForVirtualView(
            semanticsNodeIdToAccessibilityVirtualNodeId(id),
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        )
    }

    private fun checkForSemanticsChanges() {
        // Structural change
        if (isEnabledForAccessibility) {
            sendAccessibilitySemanticsStructureChangeEvents(
                view.semanticsOwner.unmergedRootSemanticsNode,
                previousSemanticsRoot
            )
        }
        if (isEnabledForContentCapture) {
            sendContentCaptureSemanticsStructureChangeEvents(
                view.semanticsOwner.unmergedRootSemanticsNode,
                previousSemanticsRoot
            )
        }
        // Property change
        sendSemanticsPropertyChangeEvents(currentSemanticsNodes)
        updateSemanticsNodesCopyAndPanes()
    }

    private fun updateSemanticsNodesCopyAndPanes() {
        // TODO(b/172606324): removed this compose specific fix when talkback has a proper solution.
        val toRemove = ArraySet<Int>()
        for (id in paneDisplayed) {
            val currentNode = currentSemanticsNodes[id]?.semanticsNode
            if (currentNode == null || !currentNode.hasPaneTitle()) {
                toRemove.add(id)
                sendPaneChangeEvents(
                    id,
                    AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED,
                    previousSemanticsNodes[id]?.unmergedConfig?.getOrNull(
                        SemanticsProperties.PaneTitle
                    )
                )
            }
        }
        paneDisplayed.removeAll(toRemove)
        previousSemanticsNodes.clear()
        for (entry in currentSemanticsNodes.entries) {
            if (entry.value.semanticsNode.hasPaneTitle() && paneDisplayed.add(entry.key)) {
                sendPaneChangeEvents(
                    entry.key,
                    AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_APPEARED,
                    entry.value.semanticsNode.unmergedConfig[SemanticsProperties.PaneTitle]
                )
            }
            previousSemanticsNodes[entry.key] =
                SemanticsNodeCopy(entry.value.semanticsNode, currentSemanticsNodes)
        }
        previousSemanticsRoot =
            SemanticsNodeCopy(view.semanticsOwner.unmergedRootSemanticsNode, currentSemanticsNodes)
    }

    @VisibleForTesting
    internal fun sendSemanticsPropertyChangeEvents(
        newSemanticsNodes: Map<Int, SemanticsNodeWithAdjustedBounds>
    ) {
        val oldScrollObservationScopes = ArrayList(scrollObservationScopes)
        scrollObservationScopes.clear()
        for (id in newSemanticsNodes.keys) {
            // We do doing this search because the new configuration is set as a whole, so we
            // can't indicate which property is changed when setting the new configuration.
            val oldNode = previousSemanticsNodes[id] ?: continue
            val newNode = newSemanticsNodes[id]?.semanticsNode
            var propertyChanged = false

            for (entry in newNode!!.unmergedConfig) {
                var newlyObservingScroll = false
                if (entry.key == SemanticsProperties.HorizontalScrollAxisRange ||
                    entry.key == SemanticsProperties.VerticalScrollAxisRange
                ) {
                    newlyObservingScroll = registerScrollingId(id, oldScrollObservationScopes)
                }
                if (!newlyObservingScroll &&
                    entry.value == oldNode.unmergedConfig.getOrNull(entry.key)
                ) {
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                when (entry.key) {
                    SemanticsProperties.Text -> {
                        val oldText = oldNode.unmergedConfig.getOrNull(SemanticsProperties.Text)
                            ?.firstOrNull()
                        val newText = newNode.unmergedConfig.getOrNull(SemanticsProperties.Text)
                            ?.firstOrNull()
                        if (oldText != newText) {
                            sendContentCaptureTextUpdateEvent(newNode.id, newText.toString())
                        }
                    }
                    SemanticsProperties.PaneTitle -> {
                        val paneTitle = entry.value as String
                        // If oldNode doesn't have pane title, it will be handled in
                        // updateSemanticsNodesCopyAndPanes().
                        if (oldNode.hasPaneTitle()) {
                            sendPaneChangeEvents(
                                id,
                                AccessibilityEventCompat.CONTENT_CHANGE_TYPE_PANE_TITLE,
                                paneTitle
                            )
                        }
                    }

                    SemanticsProperties.StateDescription, SemanticsProperties.ToggleableState -> {
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                        )
                        // Temporary(b/192295060) fix, sending CONTENT_CHANGE_TYPE_UNDEFINED to
                        // force ViewRootImpl to update its accessibility-focused virtual-node.
                        // If we have an androidx fix, we can remove this event.
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED
                        )
                    }

                    SemanticsProperties.ProgressBarRangeInfo -> {
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                        )
                        // Temporary(b/192295060) fix, sending CONTENT_CHANGE_TYPE_UNDEFINED to
                        // force ViewRootImpl to update its accessibility-focused virtual-node.
                        // If we have an androidx fix, we can remove this event.
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED
                        )
                    }

                    SemanticsProperties.Selected -> {
                        // The assumption is among widgets using SemanticsProperties.Selected, only
                        // Tab is using AccessibilityNodeInfo#isSelected, and all others are using
                        // AccessibilityNodeInfo#isChekable and setting
                        // AccessibilityNodeInfo#stateDescription in this delegate.
                        if (newNode.config.getOrNull(SemanticsProperties.Role) == Role.Tab) {
                            if (newNode.config.getOrNull(SemanticsProperties.Selected) == true) {
                                val event = createEvent(
                                    semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                    AccessibilityEvent.TYPE_VIEW_SELECTED
                                )
                                // Here we use the merged node
                                @OptIn(ExperimentalComposeUiApi::class)
                                val mergedNode = newNode.copyWithMergingEnabled()
                                val contentDescription = mergedNode.config.getOrNull(
                                    SemanticsProperties.ContentDescription
                                )?.fastJoinToString(",")
                                val text = mergedNode.config.getOrNull(SemanticsProperties.Text)
                                    ?.fastJoinToString(",")
                                contentDescription?.let { event.contentDescription = it }
                                text?.let { event.text.add(it) }
                                sendEvent(event)
                            } else {
                                // Send this event to match View.java.
                                sendEventForVirtualView(
                                    semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                                    AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED
                                )
                            }
                        } else {
                            sendEventForVirtualView(
                                semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                                AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                            )
                            // Temporary(b/192295060) fix, sending CONTENT_CHANGE_TYPE_UNDEFINED to
                            // force ViewRootImpl to update its accessibility-focused virtual-node.
                            // If we have an androidx fix, we can remove this event.
                            sendEventForVirtualView(
                                semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                                AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED
                            )
                        }
                    }

                    SemanticsProperties.ContentDescription -> {
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION,
                            entry.value as List<String>
                        )
                    }

                    SemanticsProperties.EditableText -> {
                        if (newNode.isTextField) {

                            val oldText = oldNode.unmergedConfig.getTextForTextField() ?: ""
                            val newText = newNode.unmergedConfig.getTextForTextField() ?: ""
                            val trimmedNewText = trimToSize(newText, ParcelSafeTextLength)

                            var startCount = 0
                            // endCount records how many characters are the same from the end.
                            var endCount = 0
                            val oldTextLen = oldText.length
                            val newTextLen = newText.length
                            val minLength = oldTextLen.coerceAtMost(newTextLen)
                            while (startCount < minLength) {
                                if (oldText[startCount] != newText[startCount]) {
                                    break
                                }
                                startCount++
                            }
                            // abcdabcd vs
                            //     abcd
                            while (endCount < minLength - startCount) {
                                if (oldText[oldTextLen - 1 - endCount] !=
                                    newText[newTextLen - 1 - endCount]
                                ) {
                                    break
                                }
                                endCount++
                            }
                            val removedCount = oldTextLen - endCount - startCount
                            val addedCount = newTextLen - endCount - startCount

                            // (b/247891690) We won't send a text change event when we only toggle
                            // the password visibility of the node
                            val becamePasswordNode = oldNode.semanticsNode.isTextField &&
                                !oldNode.semanticsNode.isPassword && newNode.isPassword
                            val becameNotPasswordNode = oldNode.semanticsNode.isTextField &&
                                oldNode.semanticsNode.isPassword && !newNode.isPassword
                            val event = if (becamePasswordNode || becameNotPasswordNode) {
                                // (b/247891690) password visibility toggle is handled by a
                                // selection event. Because internally Talkback already has the
                                // correct cursor position, there will be no announcement.
                                // Therefore we first send the "cursor reset" event with the
                                // selection at (0, 0) and right after that we will send the event
                                // with the correct cursor position. This behaves similarly to the
                                // View-based material EditText which also sends two selection
                                // events
                                createTextSelectionChangedEvent(
                                    virtualViewId = semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                    fromIndex = 0,
                                    toIndex = 0,
                                    itemCount = newTextLen,
                                    text = trimmedNewText
                                )
                            } else {
                                createEvent(
                                    semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                                ).apply {
                                    this.fromIndex = startCount
                                    this.removedCount = removedCount
                                    this.addedCount = addedCount
                                    this.beforeText = oldText
                                    this.text.add(trimmedNewText)
                                }
                            }
                            event.className = TextFieldClassName
                            sendEvent(event)

                            // (b/247891690) second event with the correct cursor position (see
                            // comment above for more details)
                            if (becamePasswordNode || becameNotPasswordNode) {
                                val textRange =
                                    newNode.unmergedConfig[SemanticsProperties.TextSelectionRange]
                                event.fromIndex = textRange.start
                                event.toIndex = textRange.end
                                sendEvent(event)
                            }
                        } else {
                            sendEventForVirtualView(
                                semanticsNodeIdToAccessibilityVirtualNodeId(id),
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                                AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
                            )
                        }
                    }
                    // do we need to overwrite TextRange equals?
                    SemanticsProperties.TextSelectionRange -> {
                        val newText = newNode.unmergedConfig.getTextForTextField()?.text ?: ""
                        val textRange =
                            newNode.unmergedConfig[SemanticsProperties.TextSelectionRange]
                        val event = createTextSelectionChangedEvent(
                            semanticsNodeIdToAccessibilityVirtualNodeId(id),
                            textRange.start,
                            textRange.end,
                            newText.length,
                            trimToSize(newText, ParcelSafeTextLength)
                        )
                        sendEvent(event)
                        sendPendingTextTraversedAtGranularityEvent(newNode.id)
                    }

                    SemanticsProperties.HorizontalScrollAxisRange,
                    SemanticsProperties.VerticalScrollAxisRange -> {
                        // TODO(yingleiw): Add throttling for scroll/state events.
                        notifySubtreeAccessibilityStateChangedIfNeeded(newNode.layoutNode)

                        val scope = scrollObservationScopes.findById(id)!!
                        scope.horizontalScrollAxisRange = newNode.unmergedConfig.getOrNull(
                            SemanticsProperties.HorizontalScrollAxisRange
                        )
                        scope.verticalScrollAxisRange = newNode.unmergedConfig.getOrNull(
                            SemanticsProperties.VerticalScrollAxisRange
                        )
                        sendScrollEventIfNeeded(scope)
                    }

                    SemanticsProperties.Focused -> {
                        if (entry.value as Boolean) {
                            sendEvent(
                                createEvent(
                                    semanticsNodeIdToAccessibilityVirtualNodeId(newNode.id),
                                    AccessibilityEvent.TYPE_VIEW_FOCUSED
                                )
                            )
                        }
                        // In View.java this window event is sent for unfocused view. But we send
                        // it for focused too so that TalkBack invalidates its cache. Otherwise
                        // PasteText edit option is not displayed properly on some OS versions.
                        sendEventForVirtualView(
                            semanticsNodeIdToAccessibilityVirtualNodeId(newNode.id),
                            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
                        )
                    }

                    CustomActions -> {
                        val actions = newNode.unmergedConfig[CustomActions]
                        val oldActions = oldNode.unmergedConfig.getOrNull(CustomActions)
                        if (oldActions != null) {
                            // Suppose actions with the same label should be deduped.
                            val labels = mutableSetOf<String>()
                            actions.fastForEach { action ->
                                labels.add(action.label)
                            }
                            val oldLabels = mutableSetOf<String>()
                            oldActions.fastForEach { action ->
                                oldLabels.add(action.label)
                            }
                            propertyChanged =
                                !(labels.containsAll(oldLabels) && oldLabels.containsAll(labels))
                        } else if (actions.isNotEmpty()) {
                            propertyChanged = true
                        }
                    }
                    // TODO(b/151840490) send the correct events for certain properties, like view
                    //  selected.
                    else -> {
                        if (entry.value is AccessibilityAction<*>) {
                            propertyChanged = !(entry.value as AccessibilityAction<*>)
                                .accessibilityEquals(oldNode.unmergedConfig.getOrNull(entry.key))
                        } else {
                            propertyChanged = true
                        }
                    }
                }
            }

            if (!propertyChanged) {
                propertyChanged = newNode.propertiesDeleted(oldNode)
            }
            if (propertyChanged) {
                // TODO(b/176105563): throttle the window content change events and merge different
                //  sub types. We can use the subtreeChangedLayoutNodes with sub types.
                sendEventForVirtualView(
                    semanticsNodeIdToAccessibilityVirtualNodeId(id),
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED
                )
            }
        }
    }

    private fun sendContentCaptureTextUpdateEvent(id: Int, newText: String) {
        val session = contentCaptureSession ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        // TODO: consider having a `newContentCaptureId` function to improve readability.
        val autofillId = session.newAutofillId(id.toLong())
        checkNotNull(autofillId) { "Invalid content capture ID" }
        session.notifyViewTextChanged(autofillId, newText)
    }

    // List of visible scrollable nodes (which are observing scroll state snapshot writes).
    private val scrollObservationScopes = mutableListOf<ScrollObservationScope>()

    /*
     * Lambda to store in scrolling snapshot observer, which must never be recreated because
     * the snapshot system makes use of lambda reference comparisons.
     * (Note that recent versions of the Kotlin compiler do maintain a persistent
     * object for most lambda expressions, so this is just for the purpose of explicitness.)
     */
    private val sendScrollEventIfNeededLambda: (ScrollObservationScope) -> Unit = {
        this.sendScrollEventIfNeeded(it)
    }

    private fun registerScrollingId(
        id: Int,
        oldScrollObservationScopes: List<ScrollObservationScope>
    ): Boolean {
        var newlyObservingScroll = false
        val oldScope = oldScrollObservationScopes.findById(id)
        val newScope = if (oldScope != null) {
            oldScope
        } else {
            newlyObservingScroll = true
            ScrollObservationScope(
                semanticsNodeId = id,
                allScopes = scrollObservationScopes,
                oldXValue = null,
                oldYValue = null,
                horizontalScrollAxisRange = null,
                verticalScrollAxisRange = null
            )
        }
        scrollObservationScopes.add(newScope)
        return newlyObservingScroll
    }

    private fun sendScrollEventIfNeeded(scrollObservationScope: ScrollObservationScope) {
        if (!scrollObservationScope.isValidOwnerScope) {
            return
        }
        view.snapshotObserver.observeReads(scrollObservationScope, sendScrollEventIfNeededLambda) {
            val newXState = scrollObservationScope.horizontalScrollAxisRange
            val newYState = scrollObservationScope.verticalScrollAxisRange
            val oldXValue = scrollObservationScope.oldXValue
            val oldYValue = scrollObservationScope.oldYValue

            val deltaX = if (newXState != null && oldXValue != null) {
                newXState.value() - oldXValue
            } else {
                0f
            }
            val deltaY = if (newYState != null && oldYValue != null) {
                newYState.value() - oldYValue
            } else {
                0f
            }

            if (deltaX != 0f || deltaY != 0f) {
                val virtualNodeId = semanticsNodeIdToAccessibilityVirtualNodeId(
                    scrollObservationScope.semanticsNodeId
                )

                // View System sends a content changed event before each scroll event. TalkBack
                // uses the content changed event to synchronize the focus rect along with touch
                // scroll, and uses the scroll event to hear feedback that the app reacted to scroll
                // actions that it initiated.
                sendEventForVirtualView(
                    virtualNodeId,
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                )

                val event = createEvent(
                    virtualNodeId,
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
                )
                if (newXState != null) {
                    event.scrollX = newXState.value().toInt()
                    event.maxScrollX = newXState.maxValue().toInt()
                }
                if (newYState != null) {
                    event.scrollY = newYState.value().toInt()
                    event.maxScrollY = newYState.maxValue().toInt()
                }
                sendEvent(event)
            }

            if (newXState != null) {
                scrollObservationScope.oldXValue = newXState.value()
            }
            if (newYState != null) {
                scrollObservationScope.oldYValue = newYState.value()
            }
        }
    }

    private fun sendPaneChangeEvents(
        semanticsNodeId: Int,
        contentChangeType: Int,
        title: String?
    ) {
        val event = createEvent(
            semanticsNodeIdToAccessibilityVirtualNodeId(semanticsNodeId),
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        )
        event.contentChangeTypes = contentChangeType
        if (title != null) {
            event.text.add(title)
        }
        sendEvent(event)
    }

    private fun View.getContentCaptureSessionCompat(): ContentCaptureSessionCompat? {
        ViewCompatShims.setImportantForContentCapture(
            this,
            ViewCompatShims.IMPORTANT_FOR_CONTENT_CAPTURE_YES
        )
        return ViewCompatShims.getContentCaptureSession(this)
    }

    private fun getTextLayoutResult(configuration: SemanticsConfiguration): TextLayoutResult? {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val getLayoutResult = configuration.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(textLayoutResults) ?: return null
        return if (getLayoutResult) {
            textLayoutResults[0]
        } else {
            null
        }
    }

    private fun SemanticsNode.toViewStructure(): ViewStructureCompat? {
        val session = contentCaptureSession ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        val rootAutofillId = ViewCompatShims.getAutofillId(view) ?: return null
        val parentNode = parent
        val parentAutofillId = if (parentNode != null) {
            session.newAutofillId(parentNode.id.toLong()) ?: return null
        } else {
            rootAutofillId.toAutofillId()
        }
        val structure = session.newVirtualViewStructure(
            parentAutofillId, id.toLong()) ?: return null

        val configuration = this.unmergedConfig
        if (configuration.contains(SemanticsProperties.Password)) {
            return null
        }

        configuration.getOrNull(SemanticsProperties.Text)?.let {
            structure.setClassName("android.widget.TextView")
            structure.setText(it.fastJoinToString("\n"))
        }
        configuration.getOrNull(SemanticsProperties.EditableText)?.let {
            structure.setClassName("android.widget.EditText")
            structure.setText(it)
        }
        configuration.getOrNull(SemanticsProperties.ContentDescription)?.let {
            structure.setContentDescription(it.fastJoinToString("\n"))
        }
        configuration.getOrNull(SemanticsProperties.Role)?.toLegacyClassName()?.let {
            structure.setClassName(it)
        }

        getTextLayoutResult(configuration)?.let {
            val input = it.layoutInput
            val px = input.style.fontSize.value * input.density.density * input.density.fontScale
            structure.setTextStyle(px, 0, 0, 0)
        }

        with(boundsInParent) {
            structure.setDimens(
                left.toInt(), top.toInt(), 0, 0, width.toInt(), height.toInt()
            )
        }
        return structure
    }

    private fun bufferContentCaptureViewAppeared(
        virtualId: Int,
        viewStructure: ViewStructureCompat?
    ) {
        if (viewStructure == null) {
            return
        }

        if (bufferedContentCaptureDisappearedNodes.contains(virtualId)) {
            // disappear then appear
            bufferedContentCaptureDisappearedNodes.remove(virtualId)
        } else {
            bufferedContentCaptureAppearedNodes[virtualId] = viewStructure
        }
    }
    private fun bufferContentCaptureViewDisappeared(virtualId: Int) {
        if (bufferedContentCaptureAppearedNodes.containsKey(virtualId)) {
            // appear then disappear
            bufferedContentCaptureAppearedNodes.remove(virtualId)
        } else {
            bufferedContentCaptureDisappearedNodes.add(virtualId)
        }
    }

    private fun notifyContentCaptureChanges() {
        val session = contentCaptureSession ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        if (bufferedContentCaptureAppearedNodes.isNotEmpty()) {
            session.notifyViewsAppeared(
                bufferedContentCaptureAppearedNodes.values
                    .toList()
                    .fastMap { it.toViewStructure() })
            bufferedContentCaptureAppearedNodes.clear()
        }
        if (bufferedContentCaptureDisappearedNodes.isNotEmpty()) {
            session.notifyViewsDisappeared(
                    bufferedContentCaptureDisappearedNodes
                        .toList()
                        .fastMap { it.toLong() }
                        .toLongArray())
            bufferedContentCaptureDisappearedNodes.clear()
        }
    }

    private fun updateContentCaptureBuffersOnAppeared(node: SemanticsNode) {
        if (!isEnabledForContentCapture) {
            return
        }

        updateTranslationOnAppeared(node)

        bufferContentCaptureViewAppeared(node.id, node.toViewStructure())
        node.replacedChildren.fastForEach { child -> updateContentCaptureBuffersOnAppeared(child) }
    }

    private fun updateContentCaptureBuffersOnDisappeared(node: SemanticsNode) {
        if (!isEnabledForContentCapture) {
            return
        }
        bufferContentCaptureViewDisappeared(node.id)
        node.replacedChildren.fastForEach {
                child -> updateContentCaptureBuffersOnDisappeared(child)
        }
    }

    private fun updateTranslationOnAppeared(node: SemanticsNode) {
        val config = node.unmergedConfig
        val isShowingTextSubstitution = config.getOrNull(
            SemanticsProperties.IsShowingTextSubstitution)

        if (translateStatus == TranslateStatus.SHOW_ORIGINAL &&
            isShowingTextSubstitution == true) {
            config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(false)
        } else if (translateStatus == TranslateStatus.SHOW_TRANSLATED &&
            isShowingTextSubstitution == false) {
            config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(true)
        }
    }

    internal fun onShowTranslation() {
        translateStatus = TranslateStatus.SHOW_TRANSLATED
        showTranslatedText()
    }

    internal fun onHideTranslation() {
        translateStatus = TranslateStatus.SHOW_ORIGINAL
        hideTranslatedText()
    }

    internal fun onClearTranslation() {
        translateStatus = TranslateStatus.SHOW_ORIGINAL
        clearTranslatedText()
    }

    private fun showTranslatedText() {
        for (node in currentSemanticsNodes.values) {
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) == false) {
                config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(
                    true
                )
            }
        }
    }

    private fun hideTranslatedText() {
        for (node in currentSemanticsNodes.values) {
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) == true) {
                config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(
                    false
                )
            }
        }
    }

    private fun clearTranslatedText() {
        for (node in currentSemanticsNodes.values) {
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) != null) {
                config.getOrNull(SemanticsActions.ClearTextSubstitution)?.action?.invoke()
            }
        }
    }

    private fun sendAccessibilitySemanticsStructureChangeEvents(
        newNode: SemanticsNode,
        oldNode: SemanticsNodeCopy
    ) {
        val newChildren: MutableSet<Int> = mutableSetOf()

        // If any child is added, clear the subtree rooted at this node and return.
        newNode.replacedChildren.fastForEach { child ->
            if (currentSemanticsNodes.contains(child.id)) {
                if (!oldNode.children.contains(child.id)) {
                    notifySubtreeAccessibilityStateChangedIfNeeded(newNode.layoutNode)
                    return
                }
                newChildren.add(child.id)
            }
        }

        // If any child is deleted, clear the subtree rooted at this node and return.
        for (child in oldNode.children) {
            if (!newChildren.contains(child)) {
                notifySubtreeAccessibilityStateChangedIfNeeded(newNode.layoutNode)
                return
            }
        }

        newNode.replacedChildren.fastForEach { child ->
            if (currentSemanticsNodes.contains(child.id)) {
                sendAccessibilitySemanticsStructureChangeEvents(
                    child, previousSemanticsNodes[child.id]!!)
            }
        }
    }

    internal fun initContentCapture(onStart: Boolean) {
        if (onStart) {
            updateContentCaptureBuffersOnAppeared(view.semanticsOwner.unmergedRootSemanticsNode)
        } else {
            updateContentCaptureBuffersOnDisappeared(view.semanticsOwner.unmergedRootSemanticsNode)
        }
        notifyContentCaptureChanges()
    }

    @VisibleForTesting
    internal fun sendContentCaptureSemanticsStructureChangeEvents(
        newNode: SemanticsNode,
        oldNode: SemanticsNodeCopy
    ) {
        // Iterate the new tree to notify content capture appear
        newNode.replacedChildren.fastForEach { child ->
            if (currentSemanticsNodes.contains(child.id) &&
                !oldNode.children.contains(child.id)) {
                updateContentCaptureBuffersOnAppeared(child)
            }
        }
        // Notify content capture disappear
        for (entry in previousSemanticsNodes.entries) {
            if (!currentSemanticsNodes.contains(entry.key)) {
                bufferContentCaptureViewDisappeared(entry.key)
            }
        }

        newNode.replacedChildren.fastForEach { child ->
            if (currentSemanticsNodes.contains(child.id) &&
                previousSemanticsNodes.contains(child.id)) {
                sendContentCaptureSemanticsStructureChangeEvents(
                    child, previousSemanticsNodes[child.id]!!)
            }
        }
    }

    private fun semanticsNodeIdToAccessibilityVirtualNodeId(id: Int): Int {
        if (id == view.semanticsOwner.unmergedRootSemanticsNode.id) {
            return AccessibilityNodeProviderCompat.HOST_VIEW_ID
        }
        return id
    }

    private fun traverseAtGranularity(
        node: SemanticsNode,
        granularity: Int,
        forward: Boolean,
        extendSelection: Boolean
    ): Boolean {
        if (node.id != previousTraversedNode) {
            accessibilityCursorPosition = AccessibilityCursorPositionUndefined
            previousTraversedNode = node.id
        }

        val text = getIterableTextForAccessibility(node)
        if (text.isNullOrEmpty()) {
            return false
        }
        val iterator = getIteratorForGranularity(node, granularity) ?: return false
        var current = getAccessibilitySelectionEnd(node)
        if (current == AccessibilityCursorPositionUndefined) {
            current = if (forward) 0 else text.length
        }
        val range = (if (forward) iterator.following(current) else iterator.preceding(current))
            ?: return false
        val segmentStart = range[0]
        val segmentEnd = range[1]
        var selectionStart: Int
        val selectionEnd: Int
        if (extendSelection && isAccessibilitySelectionExtendable(node)) {
            selectionStart = getAccessibilitySelectionStart(node)
            if (selectionStart == AccessibilityCursorPositionUndefined) {
                selectionStart = if (forward) segmentStart else segmentEnd
            }
            selectionEnd = if (forward) segmentEnd else segmentStart
        } else {
            selectionStart = if (forward) segmentEnd else segmentStart
            selectionEnd = selectionStart
        }
        val action =
            if (forward)
                AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            else AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
        pendingTextTraversedEvent = PendingTextTraversedEvent(
            node,
            action,
            granularity,
            segmentStart,
            segmentEnd,
            SystemClock.uptimeMillis()
        )
        setAccessibilitySelection(node, selectionStart, selectionEnd, true)
        return true
    }

    private fun sendPendingTextTraversedAtGranularityEvent(semanticsNodeId: Int) {
        pendingTextTraversedEvent?.let {
            // not the same node, do nothing. Don't set pendingTextTraversedEvent to null either.
            if (semanticsNodeId != it.node.id) {
                return
            }
            if (SystemClock.uptimeMillis() - it.traverseTime <= TextTraversedEventTimeoutMillis) {
                val event = createEvent(
                    semanticsNodeIdToAccessibilityVirtualNodeId(it.node.id),
                    AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
                )
                event.fromIndex = it.fromIndex
                event.toIndex = it.toIndex
                event.action = it.action
                event.movementGranularity = it.granularity
                event.text.add(getIterableTextForAccessibility(it.node))
                sendEvent(event)
            }
        }
        pendingTextTraversedEvent = null
    }

    private fun setAccessibilitySelection(
        node: SemanticsNode,
        start: Int,
        end: Int,
        traversalMode: Boolean
    ): Boolean {
        // Any widget which has custom action_set_selection needs to provide cursor
        // positions, so events will be sent when cursor position change.
        // When the node is disabled, only the default/virtual set selection can performed.
        if (node.unmergedConfig.contains(SemanticsActions.SetSelection) && node.enabled()) {
            // Hide all selection controllers used for adjusting selection
            // since we are doing so explicitly by other means and these
            // controllers interact with how selection behaves. From TextView.java.
            return node.unmergedConfig[SemanticsActions.SetSelection].action?.invoke(
                start,
                end,
                traversalMode
            ) ?: false
        }
        if (start == end && end == accessibilityCursorPosition) {
            return false
        }
        val text = getIterableTextForAccessibility(node) ?: return false
        accessibilityCursorPosition = if (start >= 0 && start == end && end <= text.length) {
            start
        } else {
            AccessibilityCursorPositionUndefined
        }
        val nonEmptyText = text.isNotEmpty()
        val event = createTextSelectionChangedEvent(
            semanticsNodeIdToAccessibilityVirtualNodeId(node.id),
            if (nonEmptyText) accessibilityCursorPosition else null,
            if (nonEmptyText) accessibilityCursorPosition else null,
            if (nonEmptyText) text.length else null,
            text
        )
        sendEvent(event)
        sendPendingTextTraversedAtGranularityEvent(node.id)
        return true
    }

    /** Returns selection start and end indices in original text */
    private fun getAccessibilitySelectionStart(node: SemanticsNode): Int {
        // If there is ContentDescription, it will be used instead of text during traversal.
        if (!node.unmergedConfig.contains(SemanticsProperties.ContentDescription) &&
            node.unmergedConfig.contains(SemanticsProperties.TextSelectionRange)
        ) {
            return node.unmergedConfig[SemanticsProperties.TextSelectionRange].start
        }
        return accessibilityCursorPosition
    }

    private fun getAccessibilitySelectionEnd(node: SemanticsNode): Int {
        // If there is ContentDescription, it will be used instead of text during traversal.
        if (!node.unmergedConfig.contains(SemanticsProperties.ContentDescription) &&
            node.unmergedConfig.contains(SemanticsProperties.TextSelectionRange)
        ) {
            return node.unmergedConfig[SemanticsProperties.TextSelectionRange].end
        }
        return accessibilityCursorPosition
    }

    private fun isAccessibilitySelectionExtendable(node: SemanticsNode): Boolean {
        // Currently only TextField is extendable. Static text may become extendable later.
        return !node.unmergedConfig.contains(SemanticsProperties.ContentDescription) &&
            node.unmergedConfig.contains(SemanticsProperties.EditableText)
    }

    private fun getIteratorForGranularity(
        node: SemanticsNode?,
        granularity: Int
    ): AccessibilityIterators.TextSegmentIterator? {
        if (node == null) return null

        val text = getIterableTextForAccessibility(node)
        if (text.isNullOrEmpty()) {
            return null
        }
        // TODO(b/160190186) Make sure locale is right in AccessibilityIterators.
        val iterator: AccessibilityIterators.AbstractTextSegmentIterator
        @Suppress("DEPRECATION")
        when (granularity) {
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER -> {
                iterator = AccessibilityIterators.CharacterTextSegmentIterator.getInstance(
                    view.context.resources.configuration.locale
                )
                iterator.initialize(text)
            }

            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD -> {
                iterator = AccessibilityIterators.WordTextSegmentIterator.getInstance(
                    view.context.resources.configuration.locale
                )
                iterator.initialize(text)
            }

            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH -> {
                iterator = AccessibilityIterators.ParagraphTextSegmentIterator.getInstance()
                iterator.initialize(text)
            }

            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE -> {
                // Line and page granularity are only for static text or text field.
                if (!node.unmergedConfig.contains(SemanticsActions.GetTextLayoutResult)) {
                    return null
                }
                val textLayoutResult = getTextLayoutResult(node.unmergedConfig) ?: return null
                if (granularity == AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE) {
                    iterator = AccessibilityIterators.LineTextSegmentIterator.getInstance()
                    iterator.initialize(text, textLayoutResult)
                } else {
                    iterator = AccessibilityIterators.PageTextSegmentIterator.getInstance()
                    // TODO: the node should be text/textfield node instead of the current node.
                    iterator.initialize(text, textLayoutResult, node)
                }
            }

            else -> return null
        }
        return iterator
    }

    /**
     * Gets the text reported for accessibility purposes. If a text node has a content description
     * in the unmerged config, it will be used instead of the text.
     *
     * This function is basically prioritising the content description over the text or editable
     * text of the text and text field nodes.
     */
    private fun getIterableTextForAccessibility(node: SemanticsNode?): String? {
        if (node == null) {
            return null
        }
        // Note in android framework, TextView set this to its text. This is changed to
        // prioritize content description, even for Text.
        if (node.unmergedConfig.contains(SemanticsProperties.ContentDescription)) {
            return node.unmergedConfig[SemanticsProperties.ContentDescription]
                .fastJoinToString(",")
        }

        if (node.isTextField) {
            return node.unmergedConfig.getTextForTextField()?.text
        }

        return node.unmergedConfig.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
    }

    private fun SemanticsConfiguration.getTextForTextField(): AnnotatedString? {
        return getOrNull(SemanticsProperties.EditableText)
    }

    // TODO(b/160820721): use AccessibilityNodeProviderCompat instead of AccessibilityNodeProvider
    inner class MyNodeProvider : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int):
            AccessibilityNodeInfo? {
            return createNodeInfo(virtualViewId)
        }

        override fun performAction(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            return performActionHelper(virtualViewId, action, arguments)
        }

        override fun addExtraDataToAccessibilityNodeInfo(
            virtualViewId: Int,
            info: AccessibilityNodeInfo,
            extraDataKey: String,
            arguments: Bundle?
        ) {
            addExtraDataToAccessibilityNodeInfoHelper(virtualViewId, info, extraDataKey, arguments)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private object Api24Impl {
        @DoNotInline
        @JvmStatic
        fun addSetProgressAction(
            info: AccessibilityNodeInfoCompat,
            semanticsNode: SemanticsNode
        ) {
            if (semanticsNode.enabled()) {
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.SetProgress)?.let {
                    info.addAction(
                        AccessibilityActionCompat(
                            android.R.id.accessibilityActionSetProgress,
                            it.label
                        )
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29Impl {
        @JvmStatic
        @DoNotInline
        fun addPageActions(
            info: AccessibilityNodeInfoCompat,
            semanticsNode: SemanticsNode
        ) {
            if (semanticsNode.enabled()) {
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.PageUp)?.let {
                    info.addAction(
                        AccessibilityActionCompat(
                            android.R.id.accessibilityActionPageUp,
                            it.label
                        )
                    )
                }
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.PageDown)?.let {
                    info.addAction(
                        AccessibilityActionCompat(
                            android.R.id.accessibilityActionPageDown,
                            it.label
                        )
                    )
                }
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.PageLeft)?.let {
                    info.addAction(
                        AccessibilityActionCompat(
                            android.R.id.accessibilityActionPageLeft,
                            it.label
                        )
                    )
                }
                semanticsNode.unmergedConfig.getOrNull(SemanticsActions.PageRight)?.let {
                    info.addAction(
                        AccessibilityActionCompat(
                            android.R.id.accessibilityActionPageRight,
                            it.label
                        )
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object ViewTranslationHelperMethodsS {
        @DoNotInline
        @Suppress("UNUSED_PARAMETER")
        @RequiresApi(Build.VERSION_CODES.S)
        fun onCreateVirtualViewTranslationRequests(
            accessibilityDelegateCompat: AndroidComposeViewAccessibilityDelegateCompat,
            virtualIds: LongArray,
            supportedFormats: IntArray,
            requestsCollector: Consumer<ViewTranslationRequest?>
        ) {

            virtualIds.forEach {
                val node = accessibilityDelegateCompat.currentSemanticsNodes[it.toInt()]
                    ?.semanticsNode ?: return@forEach
                val requestBuilder = ViewTranslationRequest.Builder(
                    accessibilityDelegateCompat.view.autofillId,
                    node.id.toLong()
                )

                var text = node.unmergedConfig.getOrNull(SemanticsProperties.OriginalText)
                    ?: AnnotatedString(node.getTextForTranslation() ?: return@forEach)

                requestBuilder.setValue(ViewTranslationRequest.ID_TEXT,
                    TranslationRequestValue.forText(text))
                requestsCollector.accept(requestBuilder.build())
            }
        }

        @DoNotInline
        @RequiresApi(Build.VERSION_CODES.S)
        fun onVirtualViewTranslationResponses(
            accessibilityDelegateCompat: AndroidComposeViewAccessibilityDelegateCompat,
            response: LongSparseArray<ViewTranslationResponse?>
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return
            }

            for (key in response.keyIterator()) {
                response.get(key)?.getValue(ViewTranslationRequest.ID_TEXT)?.text?.let {
                    accessibilityDelegateCompat.currentSemanticsNodes[key.toInt()]
                        ?.semanticsNode
                        ?.let { semanticsNode ->
                            semanticsNode.unmergedConfig
                                .getOrNull(SemanticsActions.SetTextSubstitution)?.action
                                ?.invoke(AnnotatedString(it.toString()))
                        }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun onCreateVirtualViewTranslationRequests(
        virtualIds: LongArray,
        supportedFormats: IntArray,
        requestsCollector: Consumer<ViewTranslationRequest?>
    ) {
        ViewTranslationHelperMethodsS.onCreateVirtualViewTranslationRequests(
            this, virtualIds, supportedFormats, requestsCollector)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun onVirtualViewTranslationResponses(
        response: LongSparseArray<ViewTranslationResponse?>
    ) {
        ViewTranslationHelperMethodsS.onVirtualViewTranslationResponses(this, response)
    }
}

private fun SemanticsNode.enabled() = (config.getOrNull(SemanticsProperties.Disabled) == null)

@OptIn(ExperimentalComposeUiApi::class)
private val SemanticsNode.isVisible: Boolean
    get() = !isTransparent && !unmergedConfig.contains(SemanticsProperties.InvisibleToUser)

private fun SemanticsNode.propertiesDeleted(
    oldNode: AndroidComposeViewAccessibilityDelegateCompat.SemanticsNodeCopy
): Boolean {
    for (entry in oldNode.unmergedConfig) {
        if (!config.contains(entry.key)) {
            return true
        }
    }
    return false
}

private fun SemanticsNode.hasPaneTitle() = config.contains(SemanticsProperties.PaneTitle)
private inline val SemanticsNode.isPassword: Boolean get() =
    config.contains(SemanticsProperties.Password)
private inline val SemanticsNode.isTextField get() =
    unmergedConfig.contains(SemanticsActions.SetText)
private inline val SemanticsNode.isRtl get() = layoutInfo.layoutDirection == LayoutDirection.Rtl
private inline val SemanticsNode.isTraversalGroup get() =
    config.getOrElse(SemanticsProperties.IsTraversalGroup) { false }
private inline val SemanticsNode.traversalIndex get() =
    config.getOrElse(SemanticsProperties.TraversalIndex) { 0f }
private val SemanticsNode.infoContentDescriptionOrNull get() = this.unmergedConfig.getOrNull(
    SemanticsProperties.ContentDescription)?.firstOrNull()

private fun SemanticsNode.getTextForTranslation(): String? = this.unmergedConfig.getOrNull(
    SemanticsProperties.Text)?.fastJoinToString("\n")

@OptIn(ExperimentalComposeUiApi::class)
private fun SemanticsNode.excludeLineAndPageGranularities(): Boolean {
    // text field that is not in focus
    if (isTextField && unmergedConfig.getOrNull(SemanticsProperties.Focused) != true) return true

    // text nodes that are part of the 'merged' text field, for example hint or label.
    val ancestor = layoutNode.findClosestParentNode {
        // looking for text field merging node
        val ancestorSemanticsConfiguration = it.collapsedSemantics
        ancestorSemanticsConfiguration?.isMergingSemanticsOfDescendants == true &&
            ancestorSemanticsConfiguration.contains(SemanticsActions.SetText)
    }
    return ancestor != null &&
        ancestor.collapsedSemantics?.getOrNull(SemanticsProperties.Focused) != true
}

private fun AccessibilityAction<*>.accessibilityEquals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AccessibilityAction<*>) return false

    if (label != other.label) return false
    if (action == null && other.action != null) return false
    if (action != null && other.action == null) return false

    return true
}

/**
 * Semantics node with adjusted bounds for the uncovered(by siblings) part.
 */
internal class SemanticsNodeWithAdjustedBounds(
    val semanticsNode: SemanticsNode,
    val adjustedBounds: android.graphics.Rect
)

private val DefaultFakeNodeBounds = Rect(0f, 0f, 10f, 10f)

/**
 * Finds pruned [SemanticsNode]s in the tree owned by this [SemanticsOwner]. A semantics node
 * completely covered by siblings drawn on top of it will be pruned. Return the results in a
 * map.
 */
internal fun SemanticsOwner.getAllUncoveredSemanticsNodesToMap():
    Map<Int, SemanticsNodeWithAdjustedBounds> {
    val root = unmergedRootSemanticsNode
    val nodes = mutableMapOf<Int, SemanticsNodeWithAdjustedBounds>()
    if (!root.layoutNode.isPlaced || !root.layoutNode.isAttached) {
        return nodes
    }

    val unaccountedSpace = with(root.boundsInRoot) {
        Region(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
    }

    fun findAllSemanticNodesRecursive(currentNode: SemanticsNode, region: Region) {
        val notAttachedOrPlaced =
            !currentNode.layoutNode.isPlaced || !currentNode.layoutNode.isAttached
        if ((unaccountedSpace.isEmpty && currentNode.id != root.id) ||
            (notAttachedOrPlaced && !currentNode.isFake)
        ) {
            return
        }
        val touchBoundsInRoot = currentNode.touchBoundsInRoot
        val left = touchBoundsInRoot.left.roundToInt()
        val top = touchBoundsInRoot.top.roundToInt()
        val right = touchBoundsInRoot.right.roundToInt()
        val bottom = touchBoundsInRoot.bottom.roundToInt()

        region.set(left, top, right, bottom)

        val virtualViewId = if (currentNode.id == root.id) {
            AccessibilityNodeProviderCompat.HOST_VIEW_ID
        } else {
            currentNode.id
        }
        if (region.op(unaccountedSpace, Region.Op.INTERSECT)) {
            nodes[virtualViewId] = SemanticsNodeWithAdjustedBounds(currentNode, region.bounds)
            // Children could be drawn outside of parent, but we are using clipped bounds for
            // accessibility now, so let's put the children recursion inside of this if. If later
            // we decide to support children drawn outside of parent, we can move it out of the
            // if block.
            val children = currentNode.replacedChildren
            for (i in children.size - 1 downTo 0) {
                findAllSemanticNodesRecursive(children[i], region)
            }
            unaccountedSpace.op(left, top, right, bottom, Region.Op.DIFFERENCE)
        } else {
            if (currentNode.isFake) {
                val parentNode = currentNode.parent
                // use parent bounds for fake node
                val boundsForFakeNode = if (parentNode?.layoutInfo?.isPlaced == true) {
                    parentNode.boundsInRoot
                } else {
                    DefaultFakeNodeBounds
                }
                nodes[virtualViewId] = SemanticsNodeWithAdjustedBounds(
                    currentNode,
                    android.graphics.Rect(
                        boundsForFakeNode.left.roundToInt(),
                        boundsForFakeNode.top.roundToInt(),
                        boundsForFakeNode.right.roundToInt(),
                        boundsForFakeNode.bottom.roundToInt(),
                    )
                )
            } else if (virtualViewId == AccessibilityNodeProviderCompat.HOST_VIEW_ID) {
                // Root view might have WRAP_CONTENT layout params in which case it will have zero
                // bounds if there is no other content with semantics. But we need to always send the
                // root view info as there are some other apps (e.g. Google Assistant) that depend
                // on accessibility info
                nodes[virtualViewId] = SemanticsNodeWithAdjustedBounds(currentNode, region.bounds)
            }
        }
    }

    findAllSemanticNodesRecursive(root, Region())
    return nodes
}

/**
 * This class is here to ensure that the classes that use this API will get verified and can be
 * AOT compiled. It is expected that this class will soft-fail verification, but the classes
 * which use this method will pass.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal object AccessibilityNodeInfoVerificationHelperMethods {
    @RequiresApi(Build.VERSION_CODES.O)
    @DoNotInline
    fun setAvailableExtraData(node: AccessibilityNodeInfo, data: List<String>) {
        node.availableExtraData = data
    }
}

/**
 * These objects are used as snapshot observation scopes for the purpose of sending accessibility
 * scroll events whenever the scroll offset changes.  There is one per scroller and their lifecycle
 * is the same as the scroller's lifecycle in the semantics tree.
 */
internal class ScrollObservationScope(
    val semanticsNodeId: Int,
    val allScopes: List<ScrollObservationScope>,
    var oldXValue: Float?,
    var oldYValue: Float?,
    var horizontalScrollAxisRange: ScrollAxisRange?,
    var verticalScrollAxisRange: ScrollAxisRange?
) : OwnerScope {
    override val isValidOwnerScope get() = allScopes.contains(this)
}

internal fun List<ScrollObservationScope>.findById(id: Int): ScrollObservationScope? {
    for (index in indices) {
        if (this[index].semanticsNodeId == id) {
            return this[index]
        }
    }
    return null
}

private fun Role.toLegacyClassName(): String? =
    when (this) {
        Role.Button -> "android.widget.Button"
        Role.Checkbox -> "android.widget.CheckBox"
        Role.RadioButton -> "android.widget.RadioButton"
        Role.Image -> "android.widget.ImageView"
        Role.DropdownList -> "android.widget.Spinner"
        else -> null
    }

/**
 * This function retrieves the View corresponding to a semanticsId, if it exists.
 */
internal fun AndroidViewsHandler.semanticsIdToView(id: Int): View? =
    layoutNodeToHolder.entries.firstOrNull { it.key.semanticsId == id }?.value

/**
 * A flag to force disable the content capture feature.
 *
 * If you find any issues with the new feature, flip this flag to true to confirm they are newly
 * introduced then file a bug.
 */
@Suppress("GetterSetterNames", "OPT_IN_MARKER_ON_WRONG_TARGET")
@get:Suppress("GetterSetterNames")
@get:ExperimentalComposeUiApi
@set:ExperimentalComposeUiApi
@ExperimentalComposeUiApi
var DisableContentCapture: Boolean by mutableStateOf(false)
