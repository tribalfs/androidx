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

package androidx.compose.material.anchoredDraggable

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.AnchoredDraggableDefaults
import androidx.compose.material.AnchoredDraggableState
import androidx.compose.material.AnchoredDraggableState.AnchorChangedCallback
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.anchoredDraggable
import androidx.compose.material.anchoredDraggable.AnchoredDraggableTestValue.A
import androidx.compose.material.anchoredDraggable.AnchoredDraggableTestValue.B
import androidx.compose.material.anchoredDraggable.AnchoredDraggableTestValue.C
import androidx.compose.material.animateTo
import androidx.compose.material.rememberAnchoredDraggableState
import androidx.compose.material.snapTo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.testutils.WithTouchSlop
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@OptIn(ExperimentalMaterialApi::class)
class AnchoredDraggableStateTest {

    @get:Rule
    val rule = createComposeRule()

    private val AnchoredDraggableTestTag = "dragbox"
    private val AnchoredDraggableBoxSize = 200.dp

    @Test
    fun anchoredDraggable_state_canSkipStateByFling() {
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        rule.setContent {
            state = rememberAnchoredDraggableState(initialValue = A)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeDown() }

        rule.waitForIdle()

        assertThat(state.currentValue).isEqualTo(C)
    }

    @Test
    fun anchoredDraggable_targetState_updatedOnSwipe() {
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        rule.setContent {
            state = rememberAnchoredDraggableState(initialValue = A)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeDown(endY = bottom * 0.45f) }
        rule.waitForIdle()
        assertThat(state.targetValue).isEqualTo(B)

        // Assert that swipe below threshold upward settles at current state
        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeUp(endY = bottom * 0.95f, durationMillis = 1000) }
        rule.waitForIdle()
        assertThat(state.targetValue).isEqualTo(B)

        // Assert that swipe below threshold downward settles at current state
        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeDown(endY = bottom * 0.05f) }
        rule.waitForIdle()
        assertThat(state.targetValue).isEqualTo(B)

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeDown(endY = bottom * 0.9f) }
        rule.waitForIdle()
        assertThat(state.targetValue).isEqualTo(C)

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeUp(endY = top * 1.1f) }
        rule.waitForIdle()
        assertThat(state.targetValue).isEqualTo(A)
    }

    @Test
    fun anchoredDraggable_targetState_updatedWithAnimation() {
        rule.mainClock.autoAdvance = false
        val animationDuration = 300
        val frameLengthMillis = 16L
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        lateinit var scope: CoroutineScope
        rule.setContent {
            val velocityThreshold = AnchoredDraggableDefaults.velocityThreshold
            state = remember(velocityThreshold) {
                AnchoredDraggableState(
                    initialValue = A,
                    animationSpec = tween(animationDuration, easing = LinearEasing),
                    positionalThreshold = { distance -> distance * 0.5f },
                    velocityThreshold = velocityThreshold
                )
            }
            scope = rememberCoroutineScope()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        scope.launch {
            state.animateTo(targetValue = B)
        }
        rule.mainClock.advanceTimeBy(1 * frameLengthMillis)

        assertWithMessage("Current state")
            .that(state.currentValue)
            .isEqualTo(A)
        assertWithMessage("Target state")
            .that(state.targetValue)
            .isEqualTo(B)

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertWithMessage("Current state")
            .that(state.currentValue)
            .isEqualTo(B)
        assertWithMessage("Target state")
            .that(state.targetValue)
            .isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_closestValue() {
        val initialValue = A
        val initialValueOffset = 0f
        val state = AnchoredDraggableState(
            initialValue = initialValue,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(
            initialValue to initialValueOffset,
            B to 200f,
            C to 400f
        )
        state.updateAnchors(anchors)

        assertThat(state.offset).isEqualTo(initialValueOffset)
        assertThat(state.currentValue).isEqualTo(A)
        assertThat(state.closestValue).isEqualTo(A)

        val aToBDistance = 200f
        val firstTargetOffset = aToBDistance * 0.4f
        state.dispatchRawDelta(firstTargetOffset)
        assertThat(state.offset).isEqualTo(firstTargetOffset)
        assertThat(state.currentValue).isEqualTo(A)
        assertThat(state.closestValue).isEqualTo(B)

        val secondTargetOffset = aToBDistance * 0.6f
        state.dispatchRawDelta(secondTargetOffset - state.offset)
        assertThat(state.offset).isEqualTo(secondTargetOffset)
        assertThat(state.currentValue).isEqualTo(A)
        assertThat(state.closestValue).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_progress_matchesSwipePosition() {
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        rule.setContent {
            state = rememberAnchoredDraggableState(initialValue = A)
            WithTouchSlop(touchSlop = 0f) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .requiredSize(AnchoredDraggableBoxSize)
                            .testTag(AnchoredDraggableTestTag)
                            .anchoredDraggable(
                                state = state,
                                orientation = Orientation.Vertical
                            )
                            .onSizeChanged { layoutSize ->
                                state.updateAnchors(
                                    mapOf(
                                        A to 0f,
                                        B to layoutSize.width / 2f,
                                        C to layoutSize.width.toFloat()
                                    )
                                )
                            }
                            .offset {
                                IntOffset(
                                    state
                                        .requireOffset()
                                        .roundToInt(), 0
                                )
                            }
                            .background(Color.Red)
                    )
                }
            }
        }

        val anchorA = state.anchors.getValue(A)
        val anchorB = state.anchors.getValue(B)
        val almostAnchorB = anchorB * 0.9f
        var expectedProgress = almostAnchorB / (anchorB - anchorA)

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeDown(endY = almostAnchorB) }

        assertThat(state.targetValue).isEqualTo(B)
        assertThat(state.progress).isEqualTo(expectedProgress)

        val almostAnchorA = anchorA + ((anchorB - anchorA) * 0.1f)
        expectedProgress = 1 - (almostAnchorA / (anchorB - anchorA))

        rule.onNodeWithTag(AnchoredDraggableTestTag)
            .performTouchInput { swipeUp(startY = anchorB, endY = almostAnchorA) }

        assertThat(state.targetValue).isEqualTo(A)
        assertThat(state.progress).isEqualTo(expectedProgress)
    }

    @Test
    fun anchoredDraggable_snapTo_updatesImmediately() = runBlocking {
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        rule.setContent {
            state = rememberAnchoredDraggableState(initialValue = A)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        state.snapTo(C)
        assertThat(state.currentValue)
            .isEqualTo(C)
    }

    @Test
    fun anchoredDraggable_rememberanchoredDraggableState_restored() {
        val restorationTester = StateRestorationTester(rule)

        val initialState = C
        val animationSpec = tween<Float>(durationMillis = 1000)
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        lateinit var scope: CoroutineScope

        restorationTester.setContent {
            state = rememberAnchoredDraggableState(initialState, animationSpec)
            SideEffect {
                state.updateAnchors(mapOf(A to 0f, B to 100f, C to 200f))
            }
            scope = rememberCoroutineScope()
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertThat(state.currentValue).isEqualTo(initialState)
        assertThat(state.animationSpec).isEqualTo(animationSpec)

        scope.launch {
            state.animateTo(B)
        }
        rule.waitForIdle()
        assertThat(state.currentValue).isEqualTo(B)

        restorationTester.emulateSavedInstanceStateRestore()
        assertThat(state.currentValue).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_targetState_accessedInInitialComposition() {
        lateinit var targetState: AnchoredDraggableTestValue
        rule.setContent {
            val state = rememberAnchoredDraggableState(initialValue = B)
            LaunchedEffect(state.targetValue) {
                targetState = state.targetValue
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Horizontal
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        assertThat(targetState).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_progress_accessedInInitialComposition() {
        var progress = Float.NaN
        rule.setContent {
            val state = rememberAnchoredDraggableState(initialValue = B)
            LaunchedEffect(state.progress) {
                progress = state.progress
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Horizontal
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        assertThat(progress).isEqualTo(1f)
    }

    @Test
    @Ignore("Todo: Fix differences between tests and real code - this shouldn't work :)")
    fun anchoredDraggable_requireOffset_accessedInInitialComposition_throws() {
        var exception: Throwable? = null
        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        var offset: Float? = null
        rule.setContent {
            state = rememberAnchoredDraggableState(initialValue = B)
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Horizontal
                        )
                        .onSizeChanged { layoutSize ->
                            state.updateAnchors(
                                mapOf(
                                    A to 0f,
                                    B to layoutSize.width / 2f,
                                    C to layoutSize.width.toFloat()
                                )
                            )
                        }
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
            exception = runCatching { offset = state.requireOffset() }.exceptionOrNull()
        }

        assertThat(state.anchors).isNotEmpty()
        assertThat(offset).isNull()
        assertThat(exception).isNotNull()
        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception).hasMessageThat().contains("offset")
    }

    @Test
    @Ignore("LaunchedEffects execute instantly in tests. How can we delay?")
    fun anchoredDraggable_requireOffset_accessedInEffect_doesntThrow() {
        var exception: Throwable? = null
        rule.setContent {
            val state = rememberAnchoredDraggableState(initialValue = B)
            LaunchedEffect(Unit) {
                exception = runCatching { state.requireOffset() }.exceptionOrNull()
            }
        }

        assertThat(exception).isNull()
    }

    @Test
    fun anchoredDraggable_animateTo_animatesBeyondBounds() {
        rule.mainClock.autoAdvance = false
        val minBound = 0f
        val maxBound = 500f
        val anchors = mapOf(
            A to minBound,
            C to maxBound
        )

        val animationSpec = FloatSpringSpec(dampingRatio = Spring.DampingRatioHighBouncy)
        val animationDuration = animationSpec.getDurationNanos(
            initialValue = minBound,
            targetValue = maxBound,
            initialVelocity = 0f
        ).let { TimeUnit.NANOSECONDS.toMillis(it) }

        lateinit var state: AnchoredDraggableState<AnchoredDraggableTestValue>
        lateinit var scope: CoroutineScope

        rule.setContent {
            scope = rememberCoroutineScope()
            state = rememberAnchoredDraggableState(
                initialValue = A,
                animationSpec = animationSpec
            )
            SideEffect {
                state.updateAnchors(anchors)
            }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .requiredSize(AnchoredDraggableBoxSize)
                        .testTag(AnchoredDraggableTestTag)
                        .anchoredDraggable(
                            state = state,
                            orientation = Orientation.Vertical
                        )
                        .offset {
                            IntOffset(
                                state
                                    .requireOffset()
                                    .roundToInt(), 0
                            )
                        }
                        .background(Color.Red)
                )
            }
        }

        scope.launch {
            state.animateTo(C)
        }
        var highestOffset = 0f
        for (i in 0..animationDuration step 16) {
            highestOffset = state.requireOffset()
            rule.mainClock.advanceTimeBy(16)
        }
        assertThat(highestOffset).isGreaterThan(anchors.getValue(C))
    }

    @Test
    fun anchoredDraggable_bounds_minBoundIsSmallestAnchor() {
        var minBound = 0f
        var maxBound = 500f
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        state.updateAnchors(
            mapOf(
                A to minBound,
                B to maxBound / 2,
                C to maxBound
            )
        )
        var size by mutableStateOf(100.dp)

        assertThat(state.minOffset).isEqualTo(minBound)
        assertThat(state.maxOffset).isEqualTo(maxBound)

        minBound *= 3
        maxBound *= 10
        state.updateAnchors(
            mapOf(
                A to minBound,
                C to maxBound
            )
        )
        size = 200.dp
        rule.waitForIdle()

        assertThat(state.minOffset).isEqualTo(minBound)
        assertThat(state.maxOffset).isEqualTo(maxBound)
    }

    @Test
    fun anchoredDraggable_targetNotInAnchors_animateTo_updatesCurrentValue() {
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        assertThat(state.anchors).isEmpty()
        assertThat(state.currentValue).isEqualTo(A)
        runBlocking { state.animateTo(B) }
        assertThat(state.currentValue).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_targetNotInAnchors_snapTo_updatesCurrentValue() {
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        assertThat(state.anchors).isEmpty()
        assertThat(state.currentValue).isEqualTo(A)
        runBlocking { state.snapTo(B) }
        assertThat(state.currentValue).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_updateAnchors_initialUpdate_initialValueInAnchors_shouldntUpdate() {
        var anchorChangeHandlerInvoked = false
        val testAnchorChangeHandler =
            AnchorChangedCallback<AnchoredDraggableTestValue> { _, _, _ ->
                anchorChangeHandlerInvoked = true
            }
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 200f, C to 300f)
        state.updateAnchors(anchors, testAnchorChangeHandler)
        assertThat(anchorChangeHandlerInvoked).isFalse()
    }

    @Test
    fun anchoredDraggable_updateAnchors_initialUpdate_initialValueNotInAnchors_shouldUpdate() {
        var anchorChangeHandlerInvoked = false
        val testAnchorChangedCallback =
            AnchorChangedCallback<AnchoredDraggableTestValue> { _, _, _ ->
                anchorChangeHandlerInvoked = true
            }
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(B to 200f, C to 300f)
        state.updateAnchors(anchors, testAnchorChangedCallback)
        assertThat(anchorChangeHandlerInvoked).isTrue()
    }

    @Test
    fun anchoredDraggable_updateAnchors_updateExistingAnchors_shouldUpdate() {
        var anchorChangeHandlerInvoked = false
        val testAnchorChangedCallback =
            AnchorChangedCallback<AnchoredDraggableTestValue> { _, _, _ ->
                anchorChangeHandlerInvoked = true
            }
        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)

        state.updateAnchors(anchors, testAnchorChangedCallback)
        assertThat(anchorChangeHandlerInvoked).isFalse()

        state.updateAnchors(mapOf(A to 100f, B to 500f, C to 700f), testAnchorChangedCallback)
        assertThat(anchorChangeHandlerInvoked).isTrue()
    }

    @Test
    fun anchoredDraggable_customDrag_updatesOffset() = runBlocking {

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)

        state.updateAnchors(anchors)
        state.anchoredDrag {
            dragTo(150f)
        }

        assertThat(state.requireOffset()).isEqualTo(150f)

        state.anchoredDrag {
            dragTo(250f)
        }
        assertThat(state.requireOffset()).isEqualTo(250f)
    }

    @Test
    fun anchoredDraggable_customDrag_updatesVelocity() = runBlocking {

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)

        state.updateAnchors(anchors)
        state.anchoredDrag {
            dragTo(150f, lastKnownVelocity = 454f)
        }
        assertThat(state.lastVelocity).isEqualTo(454f)
    }

    @Test
    fun anchoredDraggable_customDrag_targetValueUpdate() = runBlocking {
        val clock = HandPumpTestFrameClock()
        val dragScope = CoroutineScope(clock)

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)

        state.updateAnchors(anchors)
        dragScope.launch(start = CoroutineStart.UNDISPATCHED) {
            state.anchoredDrag(targetValue = C) {
                while (isActive) {
                    withFrameNanos {
                        dragTo(200f)
                    }
                }
            }
        }
        clock.advanceByFrame()
        assertThat(state.targetValue).isEqualTo(C)
        dragScope.cancel()
    }

    @Test
    fun anchoredDraggable_customDrag_anchorsPropagation() = runBlocking {
        val clock = HandPumpTestFrameClock()
        val dragScope = CoroutineScope(clock)

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)
        var providedAnchors = emptyMap<AnchoredDraggableTestValue, Float>()

        state.updateAnchors(anchors)
        dragScope.launch(start = CoroutineStart.UNDISPATCHED) {
            state.anchoredDrag(targetValue = C) { anchors ->
                providedAnchors = anchors
            }
        }
        clock.advanceByFrame()
        assertThat(providedAnchors).isEqualTo(anchors)
    }

    @Test
    fun anchoredDraggable_customDrag_doesntCallConfirm() = runBlocking {

        var counter: Int = 0

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold,
            confirmValueChange = {
                counter++
                false
            }
        )
        state.updateAnchors(mapOf(A to 0f, B to 200f, C to 300f))
        state.anchoredDrag {
            // should be B
            dragTo(200f)
        }

        assertThat(counter).isEqualTo(0)
        assertThat(state.currentValue).isEqualTo(B)
    }

    @Test
    fun anchoredDraggable_customDrag_noAnchor_doesntCallConfirm() = runBlocking {

        var counter: Int = 0

        val state = AnchoredDraggableState(
            initialValue = A,
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold,
            confirmValueChange = {
                counter++
                false
            }
        )
        state.updateAnchors(mapOf(A to 0f, B to 200f))
        state.anchoredDrag(targetValue = C) {
            // no op, doesn't matter
        }

        assertThat(counter).isEqualTo(0)
        assertThat(state.currentValue).isEqualTo(C)
    }

    @Test
    fun anchoredDraggable_updateAnchors_ongoingOffsetMutation_shouldNotUpdate() = runBlocking {
        val clock = HandPumpTestFrameClock()
        val animationScope = CoroutineScope(clock)
        val animationDuration = 2000

        var anchorChangeHandlerInvoked = false
        val testAnchorChangedCallback =
            AnchorChangedCallback<AnchoredDraggableTestValue> { _, _, _ ->
                anchorChangeHandlerInvoked = true
            }
        val state = AnchoredDraggableState(
            initialValue = A,
            animationSpec = tween(animationDuration),
            positionalThreshold = defaultPositionalThreshold,
            velocityThreshold = defaultVelocityThreshold
        )
        val anchors = mapOf(A to 0f, B to 200f, C to 300f)

        state.updateAnchors(anchors, testAnchorChangedCallback)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            state.animateTo(B)
        }
        clock.advanceByFrame()

        assertThat(state.isAnimationRunning).isTrue()

        val offsetBeforeAnchorUpdate = state.offset
        state.updateAnchors(mapOf(A to 100f, B to 500f, C to 700f), testAnchorChangedCallback)
        assertThat(offsetBeforeAnchorUpdate).isEqualTo(state.offset)
        assertThat(anchorChangeHandlerInvoked).isTrue()
    }

    private class HandPumpTestFrameClock : MonotonicFrameClock {
        private val frameCh = Channel<Long>(1)

        suspend fun advanceByFrame() {
            frameCh.send(16_000_000L)
        }

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(frameCh.receive())
        }
    }

    private val defaultPositionalThreshold: (totalDistance: Float) -> Float = {
        with(rule.density) { 56.dp.toPx() }
    }

    private val defaultVelocityThreshold: () -> Float = { with(rule.density) { 125.dp.toPx() } }
}