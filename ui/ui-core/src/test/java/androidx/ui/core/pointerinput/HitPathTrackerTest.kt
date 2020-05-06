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

package androidx.ui.core.pointerinput

import androidx.test.filters.SmallTest
import androidx.ui.core.AlignmentLine
import androidx.ui.core.CustomEvent
import androidx.ui.core.CustomEventDispatcher
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerId
import androidx.ui.core.PointerInputChange
import androidx.ui.core.PointerInputHandler
import androidx.ui.core.consumeDownChange
import androidx.ui.core.consumePositionChange
import androidx.ui.core.positionChange
import androidx.ui.testutils.down
import androidx.ui.testutils.moveTo
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.PxBounds
import androidx.ui.unit.PxPosition
import androidx.ui.unit.milliseconds
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class HitPathTrackerTest {

    private lateinit var hitPathTracker: HitPathTracker

    @Before
    fun setup() {
        hitPathTracker = HitPathTracker()
    }

    @Test
    fun addHitPath_emptyHitResult_resultIsCorrect() {
        val pif1: PointerInputFilter = mock()
        val pif2: PointerInputFilter = mock()
        val pif3: PointerInputFilter = mock()
        val pointerId = PointerId(1)

        hitPathTracker.addHitPath(pointerId, listOf(pif1, pif2, pif3))

        val expectedRoot = NodeParent().apply {
            children.add(Node(pif1).apply {
                pointerIds.add(pointerId)
                children.add(Node(pif2).apply {
                    pointerIds.add(pointerId)
                    children.add(Node(pif3).apply {
                        pointerIds.add(pointerId)
                    })
                })
            })
        }
        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    @Test
    fun addHitPath_existingNonMatchingTree_resultIsCorrect() {
        val pif1: PointerInputFilter = mock()
        val pif2: PointerInputFilter = mock()
        val pif3: PointerInputFilter = mock()
        val pif4: PointerInputFilter = mock()
        val pif5: PointerInputFilter = mock()
        val pif6: PointerInputFilter = mock()
        val pointerId1 = PointerId(1)
        val pointerId2 = PointerId(2)

        hitPathTracker.addHitPath(pointerId1, listOf(pif1, pif2, pif3))
        hitPathTracker.addHitPath(pointerId2, listOf(pif4, pif5, pif6))

        val expectedRoot = NodeParent().apply {
            children.add(Node(pif1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(pif2).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(pif3).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
            children.add(Node(pif4).apply {
                pointerIds.add(pointerId2)
                children.add(Node(pif5).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(pif6).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }
        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    @Test
    fun addHitPath_completeMatchingTree_resultIsCorrect() {
        val pif1: PointerInputFilter = mock()
        val pif2: PointerInputFilter = mock()
        val pif3: PointerInputFilter = mock()
        val pointerId1 = PointerId(1)
        val pointerId2 = PointerId(2)
        hitPathTracker.addHitPath(pointerId1, listOf(pif1, pif2, pif3))

        hitPathTracker.addHitPath(pointerId2, listOf(pif1, pif2, pif3))

        val expectedRoot = NodeParent().apply {
            children.add(Node(pif1).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                children.add(Node(pif2).apply {
                    pointerIds.add(pointerId1)
                    pointerIds.add(pointerId2)
                    children.add(Node(pif3).apply {
                        pointerIds.add(pointerId1)
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }
        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    @Test
    fun addHitPath_partiallyMatchingTree_resultIsCorrect() {
        val pif1: PointerInputFilter = mock()
        val pif2: PointerInputFilter = mock()
        val pif3: PointerInputFilter = mock()
        val pif4: PointerInputFilter = mock()
        val pif5: PointerInputFilter = mock()
        val pointerId1 = PointerId(1)
        val pointerId2 = PointerId(2)
        hitPathTracker.addHitPath(pointerId1, listOf(pif1, pif2, pif3))

        hitPathTracker.addHitPath(pointerId2, listOf(pif1, pif4, pif5))

        val expectedRoot = NodeParent().apply {
            children.add(Node(pif1).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                children.add(Node(pif2).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(pif3).apply {
                        pointerIds.add(pointerId1)
                    })
                })
                children.add(Node(pif4).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(pif5).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }
        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    @Test
    fun addHitPath_1NodeAdded_initHandlerCalledWithValidCustomMessageDispatcher() {
        val pif: PointerInputFilter = mock()

        hitPathTracker.addHitPath(PointerId(3), listOf(pif))

        verify(pif).onInit(any())
    }

    @Test
    fun addHitPath_3NodesAdded_allIitHandlersCalledWithValidCustomMessageDispatcher() {
        val pifParent: PointerInputFilter = mock()
        val pifMiddle: PointerInputFilter = mock()
        val pifChild: PointerInputFilter = mock()

        hitPathTracker.addHitPath(PointerId(3), listOf(pifParent, pifMiddle, pifChild))

        verify(pifParent).onInit(any())
        verify(pifMiddle).onInit(any())
        verify(pifChild).onInit(any())
    }

    @Test
    fun dispatchChanges_noNodes_doesNotCrash() {
        hitPathTracker.dispatchChanges(listOf(down(0)), PointerEventPass.InitialDown)
    }

    @Test
    fun dispatchChanges_hitResultHasSingleMatch_pointerInputHandlerCalled() {
        val pif: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(13), listOf(pif))

        hitPathTracker.dispatchChanges(listOf(down(13)), PointerEventPass.InitialDown)

        // Verify call count
        verify(pif).onPointerInput(any(), any(), any())
        // Verify call values
        verify(pif).onPointerInput(
            eq(listOf(down(13))),
            eq(PointerEventPass.InitialDown),
            any()
        )
    }

    @Test
    fun dispatchChanges_hitResultHasMultipleMatches_pointerInputHandlersCalledInCorrectOrder() {
        val pif1: PointerInputFilter = PointerInputFilterMock()
        val pif2: PointerInputFilter = PointerInputFilterMock()
        val pif3: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(13), listOf(pif1, pif2, pif3))

        hitPathTracker.dispatchChanges(listOf(down(13)), PointerEventPass.InitialDown)

        // Verify call count
        verify(pif1).onPointerInput(any(), any(), any())
        verify(pif2).onPointerInput(any(), any(), any())
        verify(pif3).onPointerInput(any(), any(), any())
        // Verify call order and values
        inOrder(pif1, pif2, pif3) {
            verify(pif1).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif2).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif3).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
        }
    }

    @Test
    fun dispatchChanges_hasDownAndUpPath_pointerInputHandlersCalledInCorrectOrder() {
        val pif1: PointerInputFilter = PointerInputFilterMock()
        val pif2: PointerInputFilter = PointerInputFilterMock()
        val pif3: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(13), listOf(pif1, pif2, pif3))

        hitPathTracker.dispatchChanges(
            listOf(down(13)),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        // Verify call count
        verify(pif1, times(2)).onPointerInput(any(), any(), any())
        verify(pif2, times(2)).onPointerInput(any(), any(), any())
        verify(pif3, times(2)).onPointerInput(any(), any(), any())
        // Verify call order and values
        inOrder(pif1, pif2, pif3) {
            verify(pif1).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif2).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif3).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif3).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(pif2).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(pif1).onPointerInput(
                eq(listOf(down(13))),
                eq(PointerEventPass.PreUp),
                any()
            )
        }
    }

    @Test
    fun dispatchChanges_2IndependentBranchesFromRoot_eventsSplitCorrectlyAndCallOrderCorrect() {
        val pif1: PointerInputFilter = PointerInputFilterMock()
        val pif2: PointerInputFilter = PointerInputFilterMock()
        val pif3: PointerInputFilter = PointerInputFilterMock()
        val pif4: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pif1, pif2))
        hitPathTracker.addHitPath(PointerId(5), listOf(pif3, pif4))
        val event1 = down(3)
        val event2 = down(5).moveTo(10.milliseconds, 7f, 9f)

        hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        // Verify call count
        verify(pif1, times(2)).onPointerInput(any(), any(), any())
        verify(pif2, times(2)).onPointerInput(any(), any(), any())
        verify(pif3, times(2)).onPointerInput(any(), any(), any())
        verify(pif4, times(2)).onPointerInput(any(), any(), any())
        // Verify call order and values
        inOrder(pif1, pif2) {
            verify(pif1).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif2).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif2).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(pif1).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }
        inOrder(pif3, pif4) {
            verify(pif3).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif4).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(pif4).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(pif3).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }
    }

    @Test
    fun dispatchChanges_2BranchesWithSharedParent_eventsSplitCorrectlyAndCallOrderCorrect() {
        val parent: PointerInputFilter = PointerInputFilterMock()
        val child1: PointerInputFilter = PointerInputFilterMock()
        val child2: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(parent, child1))
        hitPathTracker.addHitPath(PointerId(5), listOf(parent, child2))
        val event1 = down(3)
        val event2 = down(5).moveTo(10.milliseconds, 7f, 9f)

        hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        // Verify call count
        verify(parent, times(2)).onPointerInput(any(), any(), any())
        verify(child1, times(2)).onPointerInput(any(), any(), any())
        verify(child2, times(2)).onPointerInput(any(), any(), any())

        // Verifies that the events traverse between parent and child1 in the correct order.
        inOrder(
            parent,
            child1
        ) {
            verify(parent).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child1).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child1).onPointerInput(
                eq(listOf(event1)),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(parent).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }

        // Verifies that the events traverse between parent and child2 in the correct order.
        inOrder(
            parent,
            child2
        ) {
            verify(parent).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child2).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child2).onPointerInput(
                eq(listOf(event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
            verify(parent).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }
    }

    @Test
    fun dispatchChanges_2PointersShareCompletePath_eventsDoNotSplitAndCallOrderCorrect() {
        val child1: PointerInputFilter = PointerInputFilterMock()
        val child2: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(child1, child2))
        hitPathTracker.addHitPath(PointerId(5), listOf(child1, child2))
        val event1 = down(3)
        val event2 = down(5).moveTo(10.milliseconds, 7f, 9f)

        hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        // Verify call count
        verify(child1, times(2)).onPointerInput(any(), any(), any())
        verify(child2, times(2)).onPointerInput(any(), any(), any())

        // Verify that order is correct for child1.
        inOrder(
            child1
        ) {
            verify(child1).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child1).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }

        // Verify that order is correct for child2.
        inOrder(
            child2
        ) {
            verify(child2).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child2).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }

        // Verify that first pass hits child1 before second pass hits child2
        inOrder(
            child1,
            child2
        ) {
            verify(child1).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child2).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }

        // Verify that first pass hits child2 before second pass hits child1
        inOrder(
            child1,
            child2
        ) {
            verify(child2).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.InitialDown),
                any()
            )
            verify(child1).onPointerInput(
                eq(listOf(event1, event2)),
                eq(PointerEventPass.PreUp),
                any()
            )
        }
    }

    @Test
    fun dispatchChanges_noNodes_nothingChanges() {
        val result = hitPathTracker.dispatchChanges(listOf(down(5)), PointerEventPass.InitialDown)

        assertThat(result).isEqualTo(listOf(down(5)))
    }

    @Test
    fun dispatchChanges_hitResultHasSingleMatch_changesAreUpdatedCorrectly() {
        val pif1: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, _, _ ->
                changes.map { it.consumeDownChange() }
            })
        )
        hitPathTracker.addHitPath(PointerId(13), listOf(pif1))

        val result = hitPathTracker.dispatchChanges(listOf(down(13)), PointerEventPass.InitialDown)

        assertThat(result).isEqualTo(listOf(down(13).consumeDownChange()))
    }

    @Test
    fun dispatchChanges_hitResultHasMultipleMatchesAndDownAndUpPaths_changesAreUpdatedCorrectly() {
        val pif1: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 2f else 64f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        val pif2: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 4f else 32f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        val pif3: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 8f else 16f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        hitPathTracker.addHitPath(PointerId(13), listOf(pif1, pif2, pif3))
        val change = down(13).moveTo(10.milliseconds, 0f, 130f)

        val result = hitPathTracker.dispatchChanges(
            listOf(change),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        verify(pif1).onPointerInput(
            eq(listOf(change)), eq(PointerEventPass.InitialDown), any()
        )
        verify(pif2).onPointerInput(
            eq(listOf(change.consumePositionChange(0.px, 2.px))),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif3).onPointerInput(
            eq(listOf(change.consumePositionChange(0.px, 6.px))), // 2 + 4
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif3).onPointerInput(
            eq(listOf(change.consumePositionChange(0.px, 14.px))), // 2 + 4 + 8
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(pif2).onPointerInput(
            eq(listOf(change.consumePositionChange(0.px, 30.px))), // 2 + 4 + 8 + 16
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(pif1).onPointerInput(
            eq(listOf(change.consumePositionChange(0.px, 62.px))), // 2 + 4 + 8 + 16 + 32
            eq(PointerEventPass.PreUp),
            any()
        )
        assertThat(result)
            .isEqualTo(
                listOf(
                    change.consumePositionChange(
                        0.px,
                        126.px
                    )
                )
            ) // 2 + 4 + 8 + 16 + 32 + 64
    }

    @Test
    fun dispatchChanges_2IndependentBranchesFromRoot_changesAreUpdatedCorrectly() {
        val pif1: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 2f else 12f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        val pif2: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 3f else 6f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        val pif3: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) -2f else -12f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        val pif4: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) -3f else -6f
                    it.consumePositionChange(0.px, yConsume.px)
                }
            })
        )
        hitPathTracker.addHitPath(PointerId(3), listOf(pif1, pif2))
        hitPathTracker.addHitPath(PointerId(5), listOf(pif3, pif4))
        val event1 = down(3).moveTo(10.milliseconds, 0f, 24f)
        val event2 = down(5).moveTo(10.milliseconds, 0f, -24f)

        val result = hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        verify(pif1).onPointerInput(
            eq(listOf(event1)),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif2).onPointerInput(
            eq(listOf(event1.consumePositionChange(0.px, 2.px))),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif2).onPointerInput(
            eq(listOf(event1.consumePositionChange(0.px, 5.px))),
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(pif1).onPointerInput(
            eq(listOf(event1.consumePositionChange(0.px, 11.px))),
            eq(PointerEventPass.PreUp),
            any()
        )

        verify(pif3).onPointerInput(
            eq(listOf(event2)),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif4).onPointerInput(
            eq(listOf(event2.consumePositionChange(0.px, (-2).px))),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(pif4).onPointerInput(
            eq(listOf(event2.consumePositionChange(0.px, (-5).px))),
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(pif3).onPointerInput(
            eq(listOf(event2.consumePositionChange(0.px, (-11).px))),
            eq(PointerEventPass.PreUp),
            any()
        )

        assertThat(result).hasSize(2)
        assertThat(result).contains(event1.consumePositionChange(0.px, 23.px))
        assertThat(result).contains(event2.consumePositionChange(0.px, (-23).px))
    }

    @Test
    fun dispatchChanges_2BranchesWithSharedParent_changesAreUpdatedCorrectly() {
        val parent = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 2 else 3
                    it.consumePositionChange(
                        0.px,
                        (it.positionChange().y.value.toInt() / yConsume).px
                    )
                }
            })
        )
        val child1: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 5 else 7
                    it.consumePositionChange(
                        0.px,
                        (it.positionChange().y.value.toInt() / yConsume).px
                    )
                }
            })
        )
        val child2: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 11 else 13
                    it.consumePositionChange(
                        0.px,
                        (it.positionChange().y.value.toInt() / yConsume).px
                    )
                }
            })
        )
        hitPathTracker.addHitPath(PointerId(3), listOf(parent, child1))
        hitPathTracker.addHitPath(PointerId(5), listOf(parent, child2))
        val event1 = down(3).moveTo(10.milliseconds, 0f, 1000f)
        val event2 = down(5).moveTo(10.milliseconds, 0f, -1000f)

        val result = hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        verify(parent).onPointerInput(
            eq(listOf(event1, event2)),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(child1).onPointerInput(
            eq(listOf(event1.consumePositionChange(0.px, 500.px))),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(child2).onPointerInput(
            eq(listOf(event2.consumePositionChange(0.px, (-500).px))),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(child1).onPointerInput(
            eq(listOf(event1.consumePositionChange(0.px, 600.px))),
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(child2).onPointerInput(
            eq(listOf(event2.consumePositionChange(0.px, (-545).px))),
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(parent).onPointerInput(
            eq(
                listOf(
                    event1.consumePositionChange(0.px, 657.px),
                    event2.consumePositionChange(0.px, (-580).px)
                )
            ),
            eq(PointerEventPass.PreUp),
            any()
        )

        assertThat(result).hasSize(2)
        assertThat(result).contains(event1.consumePositionChange(0.px, 771.px))
        assertThat(result).contains(event2.consumePositionChange(0.px, (-720).px))
    }

    @Test
    fun dispatchChanges_2PointersShareCompletePath_changesAreUpdatedCorrectly() {
        val child1: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 2 else 3
                    it.consumePositionChange(
                        0.px,
                        (it.positionChange().y.value.toInt() / yConsume).px
                    )
                }
            })
        )
        val child2: PointerInputFilter = PointerInputFilterMock(
            pointerInputHandler =
            spy(StubPointerInputHandler { changes, pass, _ ->
                changes.map {
                    val yConsume = if (pass == PointerEventPass.InitialDown) 5 else 7
                    it.consumePositionChange(
                        0.px,
                        (it.positionChange().y.value.toInt() / yConsume).px
                    )
                }
            })
        )
        hitPathTracker.addHitPath(PointerId(3), listOf(child1, child2))
        hitPathTracker.addHitPath(PointerId(5), listOf(child1, child2))
        val event1 = down(3).moveTo(10.milliseconds, 0f, 1000f)
        val event2 = down(5).moveTo(10.milliseconds, 0f, -1000f)

        val result = hitPathTracker.dispatchChanges(
            listOf(event1, event2),
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp
        )

        verify(child1).onPointerInput(
            eq(listOf(event1, event2)),
            eq(PointerEventPass.InitialDown),
            any()
        )
        verify(child2).onPointerInput(
            eq(
                listOf(
                    event1.consumePositionChange(0.px, 500.px),
                    event2.consumePositionChange(0.px, (-500).px)
                )
            ),
            eq(PointerEventPass.InitialDown),
            any()
        )

        verify(child2).onPointerInput(
            eq(
                listOf(
                    event1.consumePositionChange(0.px, 600.px),
                    event2.consumePositionChange(0.px, (-600).px)
                )
            ),
            eq(PointerEventPass.PreUp),
            any()
        )
        verify(child1).onPointerInput(
            eq(
                listOf(
                    event1.consumePositionChange(0.px, 657.px),
                    event2.consumePositionChange(0.px, (-657).px)
                )
            ),
            eq(PointerEventPass.PreUp),
            any()
        )

        assertThat(result).hasSize(2)
        assertThat(result).contains(event1.consumePositionChange(0.px, 771.px))
        assertThat(result).contains(event2.consumePositionChange(0.px, (-771).px))
    }

    @Test
    fun removeDetachedPointerInputFilters_noNodes_hitResultJustHasRootAndDoesNotCrash() {
        val throwable = catchThrowable {
            hitPathTracker.removeDetachedPointerInputFilters()
        }

        assertThat(throwable).isNull()
        assertThat(areEqual(hitPathTracker.root, NodeParent()))
    }

    @Test
    fun removeDetachedPointerInputFilters_complexNothingDetached_nothingRemovedNoCancelsCalled() {

        // Arrange.

        val pif1 = PointerInputFilterMock(isAttached = true)
        val pif2 = PointerInputFilterMock(isAttached = true)
        val pif3 = PointerInputFilterMock(isAttached = true)
        val pif4 = PointerInputFilterMock(isAttached = true)
        val pif5 = PointerInputFilterMock(isAttached = true)
        val pif6 = PointerInputFilterMock(isAttached = true)
        val pif7 = PointerInputFilterMock(isAttached = true)
        val pif8 = PointerInputFilterMock(isAttached = true)
        val pif9 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(1)
        val pointerId2 = PointerId(2)
        val pointerId3 = PointerId(3)
        val pointerId4 = PointerId(4)
        val pointerId5 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(pif1))
        hitPathTracker.addHitPath(pointerId2, listOf(pif3, pif2))
        hitPathTracker.addHitPath(pointerId3, listOf(pif6, pif5, pif4))
        hitPathTracker.addHitPath(pointerId4, listOf(pif9, pif7))
        hitPathTracker.addHitPath(pointerId5, listOf(pif9, pif8))

        // Act.

        hitPathTracker.removeDetachedPointerInputFilters()

        // Assert.

        val expectedRoot = NodeParent().apply {
            children.add(Node(pif1).apply {
                pointerIds.add(pointerId1)
            })
            children.add(Node(pif3).apply {
                pointerIds.add(pointerId2)
                children.add(Node(pif2).apply {
                    pointerIds.add(pointerId2)
                })
            })
            children.add(Node(pif6).apply {
                pointerIds.add(pointerId3)
                children.add(Node(pif5).apply {
                    pointerIds.add(pointerId3)
                    children.add(Node(pif4).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
            children.add(Node(pif9).apply {
                pointerIds.add(pointerId4)
                pointerIds.add(pointerId5)
                children.add(Node(pif7).apply {
                    pointerIds.add(pointerId4)
                })
                children.add(Node(pif8).apply {
                    pointerIds.add(pointerId5)
                })
            })
        }
        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()

        verify(pif1, never()).onCancel()
        verify(pif2, never()).onCancel()
        verify(pif3, never()).onCancel()
        verify(pif4, never()).onCancel()
        verify(pif5, never()).onCancel()
        verify(pif7, never()).onCancel()
        verify(pif8, never()).onCancel()
        verify(pif9, never()).onCancel()
    }

    //  compositionRoot, root -> middle -> leaf
    @Test
    fun removeDetachedPointerInputFilters_1PathRootDetached_allRemovedAndCorrectCancels() {
        val root = PointerInputFilterMock(isAttached = false)
        val middle = PointerInputFilterMock(isAttached = false)
        val leaf = PointerInputFilterMock(isAttached = false)

        hitPathTracker.addHitPath(PointerId(0), listOf(root, middle, leaf))

        hitPathTracker.removeDetachedPointerInputFilters()

        assertThat(areEqual(hitPathTracker.root, NodeParent())).isTrue()
        inOrder(leaf, middle, root) {
            verify(leaf).onCancel()
            verify(middle).onCancel()
            verify(root).onCancel()
        }
    }

    //  compositionRoot -> root, middle -> child
    @Test
    fun removeDetachedPointerInputFilters_1PathMiddleDetached_removesAndCancelsCorrect() {
        val root = PointerInputFilterMock(isAttached = true)
        val middle = PointerInputFilterMock(isAttached = false)
        val child = PointerInputFilterMock(isAttached = false)

        val pointerId = PointerId(0)
        hitPathTracker.addHitPath(pointerId, listOf(root, middle, child))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId)
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(child, middle) {
            verify(child).onCancel()
            verify(middle).onCancel()
        }
        verify(root, never()).onCancel()
    }

    //  compositionRoot -> root -> middle, leaf
    @Test
    fun removeDetachedPointerInputFilters_1PathLeafDetached_removesAndCancelsCorrect() {
        val root = PointerInputFilterMock(isAttached = true)
        val middle = PointerInputFilterMock(isAttached = true)
        val leaf = PointerInputFilterMock(isAttached = false)

        val pointerId = PointerId(0)
        hitPathTracker.addHitPath(pointerId, listOf(root, middle, leaf))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf).onCancel()
        verify(middle, never()).onCancel()
        verify(root, never()).onCancel()
    }

    //  compositionRoot -> root1 -> middle1 -> leaf1
    //  compositionRoot -> root2 -> middle2 -> leaf2
    //  compositionRoot, root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots1Detached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = true)
        val leaf1 = PointerInputFilterMock(isAttached = true)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = true)

        val root3 = PointerInputFilterMock(isAttached = false)
        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(leaf2).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()

        verify(leaf1, never()).onCancel()
        verify(middle1, never()).onCancel()
        verify(root1, never()).onCancel()
        verify(leaf2, never()).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        inOrder(leaf3, middle3, root3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
            verify(root3).onCancel()
        }
    }

    //  compositionRoot -> root1, middle1 -> leaf1
    //  compositionRoot -> root2 -> middle2 -> leaf2
    //  compositionRoot -> root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots1MiddleDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = true)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(leaf2).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf3).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
        }
        verify(root1, never()).onCancel()
        verify(leaf2, never()).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        verify(leaf3, never()).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    //  compositionRoot -> root1 -> middle1 -> leaf1
    //  compositionRoot -> root2 -> middle2, leaf2
    //  compositionRoot -> root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots1LeafDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = true)
        val leaf1 = PointerInputFilterMock(isAttached = true)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                })
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf3).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1, never()).onCancel()
        verify(middle1, never()).onCancel()
        verify(root1, never()).onCancel()
        verify(leaf2).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        verify(leaf3, never()).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    //  compositionRoot, root1 -> middle1 -> leaf1
    //  compositionRoot -> root2 -> middle2 -> leaf2
    //  compositionRoot, root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots2Detached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = false)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = true)

        val root3 = PointerInputFilterMock(isAttached = false)
        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(leaf2).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()

        inOrder(leaf1, middle1, root1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
            verify(root1).onCancel()
        }
        verify(leaf2, never()).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        inOrder(leaf3, middle3, root3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
            verify(root3).onCancel()
        }
    }

    //  compositionRoot -> root1, middle1 -> leaf1
    //  compositionRoot -> root2, middle2 -> leaf2
    //  compositionRoot -> root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots2MiddlesDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf3).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()

        inOrder(leaf1, middle1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
        }
        verify(root1, never()).onCancel()
        inOrder(leaf2, middle2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
        }
        verify(root2, never()).onCancel()
        verify(leaf3, never()).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    //  compositionRoot -> root1 -> middle1 -> leaf1
    //  compositionRoot -> root2 -> middle2, leaf2
    //  compositionRoot -> root3 -> middle3, leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots2LeafsDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = true)
        val leaf1 = PointerInputFilterMock(isAttached = true)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                })
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1, never()).onCancel()
        verify(middle1, never()).onCancel()
        verify(root1, never()).onCancel()
        verify(leaf2).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        verify(leaf3).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    //  compositionRoot, root1 -> middle1 -> leaf1
    //  compositionRoot, root2 -> middle2 -> leaf2
    //  compositionRoot, root3 -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots3Detached_allRemovedAndCancelsCorrect() {
        val root1 = PointerInputFilterMock(isAttached = false)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = false)
        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = false)
        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        hitPathTracker.addHitPath(PointerId(3), listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(PointerId(5), listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(PointerId(7), listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent()

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1, root1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
            verify(root1).onCancel()
        }
        inOrder(leaf2, middle2, root2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
            verify(root2).onCancel()
        }
        inOrder(leaf3, middle3, root3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
            verify(root3).onCancel()
        }
    }

    //  compositionRoot -> root1, middle1 -> leaf1
    //  compositionRoot -> root2, middle2 -> leaf2
    //  compositionRoot -> root3, middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots3MiddlesDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
        }
        verify(root1, never()).onCancel()
        inOrder(leaf2, middle2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
        }
        verify(root2, never()).onCancel()
        inOrder(leaf3, middle3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
        }
        verify(root3, never()).onCancel()
    }

    //  compositionRoot -> root1 -> middle1, leaf1
    //  compositionRoot -> root2 -> middle2, leaf2
    //  compositionRoot -> root3 -> middle3, leaf3
    @Test
    fun removeDetachedPointerInputFilters_3Roots3LeafsDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = true)
        val middle1 = PointerInputFilterMock(isAttached = true)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                })
            })
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                })
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1).onCancel()
        verify(middle1, never()).onCancel()
        verify(root1, never()).onCancel()
        verify(leaf2).onCancel()
        verify(middle2, never()).onCancel()
        verify(root2, never()).onCancel()
        verify(leaf3).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    // compositionRoot, root1 -> middle1 -> leaf1
    // compositionRoot -> root2, middle2, leaf2
    // compositionRoot -> root3 -> middle3, leaf3
    @Test
    fun removeDetachedPointerInputFilters_3RootsStaggeredDetached_removesAndCancelsCorrect() {

        val root1 = PointerInputFilterMock(isAttached = false)
        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val root2 = PointerInputFilterMock(isAttached = true)
        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val root3 = PointerInputFilterMock(isAttached = true)
        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root3, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root2).apply {
                pointerIds.add(pointerId2)
            })
            children.add(Node(root3).apply {
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1, root1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
            verify(root1).onCancel()
        }
        inOrder(leaf2, middle2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
        }
        verify(root2, never()).onCancel()
        verify(leaf3).onCancel()
        verify(middle3, never()).onCancel()
        verify(root3, never()).onCancel()
    }

    // compositionRoot, root ->
    //   middle1 -> leaf1
    //   middle2 -> leaf2
    //   middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_rootWith3MiddlesDetached_allRemovedAndCorrectCancels() {
        val root = PointerInputFilterMock(isAttached = false)

        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        hitPathTracker.addHitPath(PointerId(3), listOf(root, middle1, leaf1))
        hitPathTracker.addHitPath(PointerId(5), listOf(root, middle2, leaf2))
        hitPathTracker.addHitPath(PointerId(7), listOf(root, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent()

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1, root) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
            verify(root).onCancel()
        }
        inOrder(leaf2, middle2, root) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
            verify(root).onCancel()
        }
        inOrder(leaf3, middle3, root) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
            verify(root).onCancel()
        }
    }

    // compositionRoot -> root
    //   -> middle1 -> leaf1
    //   -> middle2 -> leaf2
    //   , middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_rootWith3Middles1Detached_removesAndCancelsCorrect() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle1 = PointerInputFilterMock(isAttached = true)
        val leaf1 = PointerInputFilterMock(isAttached = true)

        val middle2 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = true)

        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                })
                children.add(Node(middle2).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(leaf2).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf3, middle3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
        }
        verify(leaf2, never()).onCancel()
        verify(middle2, never()).onCancel()
        verify(leaf1, never()).onCancel()
        verify(middle1, never()).onCancel()
        verify(root, never()).onCancel()
    }

    // compositionRoot -> root
    //   , middle1 -> leaf1
    //   , middle2 -> leaf2
    //   -> middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_rootWith3Middles2Detached_removesAndCancelsCorrect() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val middle3 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
                children.add(Node(middle3).apply {
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf3).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
        }
        inOrder(leaf2, middle2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
        }
        verify(leaf3, never()).onCancel()
        verify(middle3, never()).onCancel()
        verify(root, never()).onCancel()
    }

    // compositionRoot -> root
    //   , middle1 -> leaf1
    //   , middle2 -> leaf2
    //   , middle3 -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_rootWith3MiddlesAllDetached_allMiddlesRemoved() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle1 = PointerInputFilterMock(isAttached = false)
        val leaf1 = PointerInputFilterMock(isAttached = false)

        val middle2 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)

        val middle3 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle2, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root, middle3, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        inOrder(leaf1, middle1) {
            verify(leaf1).onCancel()
            verify(middle1).onCancel()
        }
        inOrder(leaf2, middle2) {
            verify(leaf2).onCancel()
            verify(middle2).onCancel()
        }
        inOrder(leaf3, middle3) {
            verify(leaf3).onCancel()
            verify(middle3).onCancel()
        }
        verify(root, never()).onCancel()
    }

    // compositionRoot -> root -> middle
    //   -> leaf1
    //   , leaf2
    //   -> leaf3
    @Test
    fun removeDetachedPointerInputFilters_middleWith3Leafs1Detached_correctLeafRemoved() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle = PointerInputFilterMock(isAttached = true)

        val leaf1 = PointerInputFilterMock(isAttached = true)
        val leaf2 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = true)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle, leaf2))
        hitPathTracker.addHitPath(pointerId3, listOf(root, middle, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    pointerIds.add(pointerId2)
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                    children.add(Node(leaf3).apply {
                        pointerIds.add(pointerId3)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1, never()).onCancel()
        verify(leaf2).onCancel()
        verify(leaf3, never()).onCancel()
        verify(middle, never()).onCancel()
        verify(root, never()).onCancel()
    }

    // compositionRoot -> root -> middle
    //   , leaf1
    //   -> leaf2
    //   , leaf3
    @Test
    fun removeDetachedPointerInputFilters_middleWith3Leafs2Detached_correctLeafsRemoved() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle = PointerInputFilterMock(isAttached = true)

        val leaf1 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = true)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(PointerId(3), listOf(root, middle, leaf1))
        hitPathTracker.addHitPath(PointerId(5), listOf(root, middle, leaf2))
        hitPathTracker.addHitPath(PointerId(7), listOf(root, middle, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    pointerIds.add(pointerId2)
                    pointerIds.add(pointerId3)
                    children.add(Node(leaf2).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1).onCancel()
        verify(leaf2, never()).onCancel()
        verify(leaf3).onCancel()
        verify(middle, never()).onCancel()
        verify(root, never()).onCancel()
    }

    // compositionRoot -> root -> middle
    //   , leaf1
    //   , leaf2
    //   , leaf3
    @Test
    fun removeDetachedPointerInputFilters_middleWith3LeafsAllDetached_allLeafsRemoved() {

        val root = PointerInputFilterMock(isAttached = true)

        val middle = PointerInputFilterMock(isAttached = true)

        val leaf1 = PointerInputFilterMock(isAttached = false)
        val leaf2 = PointerInputFilterMock(isAttached = false)
        val leaf3 = PointerInputFilterMock(isAttached = false)

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)
        val pointerId3 = PointerId(7)

        hitPathTracker.addHitPath(PointerId(3), listOf(root, middle, leaf1))
        hitPathTracker.addHitPath(PointerId(5), listOf(root, middle, leaf2))
        hitPathTracker.addHitPath(PointerId(7), listOf(root, middle, leaf3))

        hitPathTracker.removeDetachedPointerInputFilters()

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                pointerIds.add(pointerId2)
                pointerIds.add(pointerId3)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    pointerIds.add(pointerId2)
                    pointerIds.add(pointerId3)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
        verify(leaf1).onCancel()
        verify(leaf2).onCancel()
        verify(leaf3).onCancel()
        verify(middle, never()).onCancel()
        verify(root, never()).onCancel()
    }

    // arrange: root(3) -> middle(3) -> leaf(3)
    // act: 3 is removed
    // assert: no path
    @Test
    fun removeHitPath_onePathPointerIdRemoved_hitTestResultIsEmpty() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId = PointerId(3)

        hitPathTracker.addHitPath(PointerId(3), listOf(root, middle, leaf))

        hitPathTracker.removeHitPath(pointerId)

        val expectedRoot = NodeParent()

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // arrange: root(3) -> middle(3) -> leaf(3)
    // act: 99 is removed
    // assert: root(3) -> middle(3) -> leaf(3)
    @Test
    fun removeHitPath_onePathOtherPointerIdRemoved_hitTestResultIsNotChanged() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(99)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))

        hitPathTracker.removeHitPath(pointerId2)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // Arrange:
    // root(3) -> middle(3) -> leaf(3)
    // root(5) -> middle(5) -> leaf(5)
    //
    // Act:
    // 5 is removed
    //
    // Act:
    // root(3) -> middle(3) -> leaf(3)
    @Test
    fun removeHitPath_2IndependentPaths1PointerIdRemoved_resultContainsRemainingPath() {
        val root1: PointerInputFilter = mock()
        val middle1: PointerInputFilter = mock()
        val leaf1: PointerInputFilter = mock()

        val root2: PointerInputFilter = mock()
        val middle2: PointerInputFilter = mock()
        val leaf2: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root1, middle1, leaf1))
        hitPathTracker.addHitPath(pointerId2, listOf(root2, middle2, leaf2))

        hitPathTracker.removeHitPath(pointerId2)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root1).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle1).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf1).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // root(3,5) -> middle(3,5) -> leaf(3,5)
    // 3 is removed
    // root(5) -> middle(5) -> leaf(5)
    @Test
    fun removeHitPath_2PathsShareNodes1PointerIdRemoved_resultContainsRemainingPath() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle, leaf))

        hitPathTracker.removeHitPath(pointerId1)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId2)
                    children.add(Node(leaf).apply {
                        pointerIds.add(pointerId2)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // Arrange: root(3,5) -> middle(3,5) -> leaf(3)
    // Act: 3 is removed
    // Assert: root(5) -> middle(5)
    @Test
    fun removeHitPath_2PathsShare2NodesLongPathPointerIdRemoved_resultJustHasShortPath() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle))

        hitPathTracker.removeHitPath(pointerId1)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId2)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId2)
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // Arrange: root(3,5) -> middle(3,5) -> leaf(3)
    // Act: 5 is removed
    // Assert: root(3) -> middle(3) -> leaf(3)
    @Test
    fun removeHitPath_2PathsShare2NodesShortPathPointerIdRemoved_resultJustHasLongPath() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))
        hitPathTracker.addHitPath(pointerId2, listOf(root, middle))

        hitPathTracker.removeHitPath(pointerId2)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // Arrange: root(3,5) -> middle(3) -> leaf(3)
    // Act: 3 is removed
    // Assert: root(5)
    @Test
    fun removeHitPath_2PathsShare1NodeLongPathPointerIdRemoved_resultJustHasShortPath() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))
        hitPathTracker.addHitPath(pointerId2, listOf(root))

        hitPathTracker.removeHitPath(pointerId1)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId2)
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    // Arrange: root(3,5) -> middle(3) -> leaf(3)
    // Act: 5 is removed
    // Assert: root(3) -> middle(3) -> leaf(3)
    @Test
    fun removeHitPath_2PathsShare1NodeShortPathPointerIdRemoved_resultJustHasLongPath() {
        val root: PointerInputFilter = mock()
        val middle: PointerInputFilter = mock()
        val leaf: PointerInputFilter = mock()

        val pointerId1 = PointerId(3)
        val pointerId2 = PointerId(5)

        hitPathTracker.addHitPath(pointerId1, listOf(root, middle, leaf))
        hitPathTracker.addHitPath(pointerId2, listOf(root))

        hitPathTracker.removeHitPath(pointerId2)

        val expectedRoot = NodeParent().apply {
            children.add(Node(root).apply {
                pointerIds.add(pointerId1)
                children.add(Node(middle).apply {
                    pointerIds.add(pointerId1)
                    children.add(Node(leaf).apply {
                        pointerIds.add(pointerId1)
                    })
                })
            })
        }

        assertThat(areEqual(hitPathTracker.root, expectedRoot)).isTrue()
    }

    @Test
    fun processCancel_nothingTracked_doesNotCrash() {
        hitPathTracker.processCancel()
    }

    // Pin -> Ln
    @Test
    fun processCancel_singlePin_cancelHandlerIsCalled() {
        val pointerInputNode: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pointerInputNode))

        hitPathTracker.processCancel()

        verify(pointerInputNode).onCancel()
    }

    // Pin -> Pin -> Pin
    @Test
    fun processCancel_3Pins_cancelHandlersCalledOnceInOrder() {
        val pointerInputNodeChild: PointerInputFilter = PointerInputFilterMock()
        val pointerInputNodeMiddle: PointerInputFilter = PointerInputFilterMock()
        val pointerInputNodeParent: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(
            PointerId(3),
            listOf(pointerInputNodeParent, pointerInputNodeMiddle, pointerInputNodeChild)
        )

        hitPathTracker.processCancel()

        inOrder(
            pointerInputNodeParent,
            pointerInputNodeMiddle,
            pointerInputNodeChild
        ) {
            verify(pointerInputNodeChild).onCancel()
            verify(pointerInputNodeMiddle).onCancel()
            verify(pointerInputNodeParent).onCancel()
        }
    }

    // PIN -> PIN
    // PIN -> PIN
    @Test
    fun processCancel_2IndependentPathsFromRoot_cancelHandlersCalledOnceInOrder() {
        val pifParent1: PointerInputFilter = PointerInputFilterMock()
        val pifChild1: PointerInputFilter = PointerInputFilterMock()
        val pifParent2: PointerInputFilter = PointerInputFilterMock()
        val pifChild2: PointerInputFilter = PointerInputFilterMock()

        hitPathTracker.addHitPath(PointerId(3), listOf(pifParent1, pifChild1))
        hitPathTracker.addHitPath(PointerId(5), listOf(pifParent2, pifChild2))

        hitPathTracker.processCancel()

        inOrder(pifParent1, pifChild1) {
            verify(pifChild1).onCancel()
            verify(pifParent1).onCancel()
        }
        inOrder(pifParent2, pifChild2) {
            verify(pifChild2).onCancel()
            verify(pifParent2).onCancel()
        }
    }

    // PIN -> PIN
    //     -> PIN
    @Test
    fun processCancel_2BranchingPaths_cancelHandlersCalledOnceInOrder() {
        val pifParent: PointerInputFilter = PointerInputFilterMock()
        val pifChild1: PointerInputFilter = PointerInputFilterMock()
        val pifChild2: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pifParent, pifChild1))
        hitPathTracker.addHitPath(PointerId(5), listOf(pifParent, pifChild2))

        hitPathTracker.processCancel()

        inOrder(pifParent, pifChild1) {
            verify(pifChild1).onCancel()
            verify(pifParent).onCancel()
        }
        inOrder(pifParent, pifChild2) {
            verify(pifChild2).onCancel()
            verify(pifParent).onCancel()
        }
    }

    // Pin -> Ln
    @Test
    fun processCancel_singlePin_cleared() {
        val pointerInputNode: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pointerInputNode))

        hitPathTracker.processCancel()

        assertThat(areEqual(hitPathTracker.root, NodeParent())).isTrue()
    }

    // Pin -> Pin -> Pin
    @Test
    fun processCancel_3Pins_cleared() {
        val pointerInputNodeChild: PointerInputFilter = PointerInputFilterMock()
        val pointerInputNodeMiddle: PointerInputFilter = PointerInputFilterMock()
        val pointerInputNodeParent: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(
            PointerId(3),
            listOf(pointerInputNodeParent, pointerInputNodeMiddle, pointerInputNodeChild)
        )

        hitPathTracker.processCancel()

        assertThat(areEqual(hitPathTracker.root, NodeParent())).isTrue()
    }

    // PIN -> PIN
    // PIN -> PIN
    @Test
    fun processCancel_2IndependentPathsFromRoot_cleared() {
        val pifParent1: PointerInputFilter = PointerInputFilterMock()
        val pifChild1: PointerInputFilter = PointerInputFilterMock()
        val pifParent2: PointerInputFilter = PointerInputFilterMock()
        val pifChild2: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pifParent1, pifChild1))
        hitPathTracker.addHitPath(PointerId(5), listOf(pifParent2, pifChild2))

        hitPathTracker.processCancel()

        assertThat(areEqual(hitPathTracker.root, NodeParent())).isTrue()
    }

    // PIN -> PIN
    //     -> PIN
    @Test
    fun processCancel_2BranchingPaths_cleared() {
        val pifParent: PointerInputFilter = PointerInputFilterMock()
        val pifChild1: PointerInputFilter = PointerInputFilterMock()
        val pifChild2: PointerInputFilter = PointerInputFilterMock()
        hitPathTracker.addHitPath(PointerId(3), listOf(pifParent, pifChild1))
        hitPathTracker.addHitPath(PointerId(5), listOf(pifParent, pifChild2))

        hitPathTracker.processCancel()

        assertThat(areEqual(hitPathTracker.root, NodeParent())).isTrue()
    }

    @Test
    fun dispatchCustomEvent_1NodeItDispatches_nothingReceivesDispatch() {

        // Arrange

        lateinit var dispatcher: CustomEventDispatcher

        val pif: PointerInputFilter = PointerInputFilterMock(
            initHandler = { dispatcher = it }
        )

        hitPathTracker.addHitPath(PointerId(3), listOf(pif))

        val event = TestCustomEvent("test")

        // Act

        dispatcher.dispatchCustomEvent(event)

        // Assert

        verify(pif, never()).onCustomEvent(any(), any())
    }

    @Test
    fun dispatchCustomEvent_1Path3NodesParentDispatches_dispatchCorrect() {
        dispatchCustomEvent_1Path3Nodes_dispatchCorrect(DispatchingPif.Parent)
    }

    @Test
    fun dispatchCustomEvent_1Path3NodesMiddleDispatches_dispatchCorrect() {
        dispatchCustomEvent_1Path3Nodes_dispatchCorrect(DispatchingPif.Middle)
    }

    @Test
    fun dispatchCustomEvent_1Path3NodesChildDispatches_dispatchCorrect() {
        dispatchCustomEvent_1Path3Nodes_dispatchCorrect(DispatchingPif.Child)
    }

    private enum class DispatchingPif {
        Parent, Middle, Child
    }

    private fun dispatchCustomEvent_1Path3Nodes_dispatchCorrect(
        dispatchingPif: DispatchingPif
    ) {
        // Arrange

        lateinit var dispatcher: CustomEventDispatcher
        lateinit var parentPif: PointerInputFilter
        lateinit var middlePif: PointerInputFilter
        lateinit var childPif: PointerInputFilter

        lateinit var pifThatDispatches: PointerInputFilter
        lateinit var seniorPif: PointerInputFilter
        lateinit var juniorPif: PointerInputFilter
        val dispatcherInitHandler: (CustomEventDispatcher) -> Unit = { dispatcher = it }

        when (dispatchingPif) {
            DispatchingPif.Parent -> {
                parentPif = PointerInputFilterMock(
                    initHandler = dispatcherInitHandler
                )
                pifThatDispatches = parentPif
                middlePif = PointerInputFilterMock()
                seniorPif = middlePif
                childPif = PointerInputFilterMock()
                juniorPif = childPif
            }
            DispatchingPif.Middle -> {
                parentPif = PointerInputFilterMock()
                seniorPif = parentPif
                middlePif = PointerInputFilterMock(
                    initHandler = dispatcherInitHandler
                )
                pifThatDispatches = middlePif
                childPif = PointerInputFilterMock()
                juniorPif = childPif
            }
            DispatchingPif.Child -> {
                parentPif = PointerInputFilterMock()
                seniorPif = parentPif
                middlePif = PointerInputFilterMock()
                juniorPif = middlePif
                childPif = PointerInputFilterMock(
                    initHandler = dispatcherInitHandler
                )
                pifThatDispatches = childPif
            }
        }

        hitPathTracker.addHitPath(PointerId(3), listOf(parentPif, middlePif, childPif))

        val event = TestCustomEvent("test")

        // Act

        dispatcher.dispatchCustomEvent(event)

        // Assert

        verify(seniorPif, times(5)).onCustomEvent(any(), any())
        verify(juniorPif, times(5)).onCustomEvent(any(), any())
        inOrder(seniorPif, juniorPif) {
            verify(seniorPif).onCustomEvent(event, PointerEventPass.InitialDown)
            verify(juniorPif).onCustomEvent(event, PointerEventPass.InitialDown)
            verify(juniorPif).onCustomEvent(event, PointerEventPass.PreUp)
            verify(seniorPif).onCustomEvent(event, PointerEventPass.PreUp)
            verify(seniorPif).onCustomEvent(event, PointerEventPass.PreDown)
            verify(juniorPif).onCustomEvent(event, PointerEventPass.PreDown)
            verify(juniorPif).onCustomEvent(event, PointerEventPass.PostUp)
            verify(seniorPif).onCustomEvent(event, PointerEventPass.PostUp)
            verify(seniorPif).onCustomEvent(event, PointerEventPass.PostDown)
            verify(juniorPif).onCustomEvent(event, PointerEventPass.PostDown)
        }
        verify(pifThatDispatches, never()).onCustomEvent(any(), any())
    }

    @Test
    fun dispatchCustomEvent_1Parent2ChildrenParentDispatches_dispatchCorrect() {

        lateinit var dispatcher: CustomEventDispatcher

        val parentPin = PointerInputFilterMock(initHandler = { dispatcher = it })
        val childPin1 = PointerInputFilterMock()
        val childPin2 = PointerInputFilterMock()

        hitPathTracker.addHitPath(PointerId(3), listOf(parentPin, childPin1))
        hitPathTracker.addHitPath(PointerId(4), listOf(parentPin, childPin2))

        val event = TestCustomEvent("test")

        // Act

        dispatcher.dispatchCustomEvent(event)

        // Assert
        inOrder(childPin1) {
            verify(childPin1).onCustomEvent(event, PointerEventPass.InitialDown)
            verify(childPin1).onCustomEvent(event, PointerEventPass.PreUp)
            verify(childPin1).onCustomEvent(event, PointerEventPass.PreDown)
            verify(childPin1).onCustomEvent(event, PointerEventPass.PostUp)
            verify(childPin1).onCustomEvent(event, PointerEventPass.PostDown)
        }
        inOrder(childPin2) {
            verify(childPin2).onCustomEvent(event, PointerEventPass.InitialDown)
            verify(childPin2).onCustomEvent(event, PointerEventPass.PreUp)
            verify(childPin2).onCustomEvent(event, PointerEventPass.PreDown)
            verify(childPin2).onCustomEvent(event, PointerEventPass.PostUp)
            verify(childPin2).onCustomEvent(event, PointerEventPass.PostDown)
        }
        verify(parentPin, never()).onCustomEvent(any(), any())
    }

    @Test
    fun dispatchCustomEvent_1Parent2ChildrenChild1Dispatches_dispatchCorrect() {
        dispatchCustomEvent_1Parent2ChildrenChildDispatches_dispatchCorrect(
            true
        )
    }

    @Test
    fun dispatchCustomEvent_1Parent2ChildrenChild2Dispatches_dispatchCorrect() {
        dispatchCustomEvent_1Parent2ChildrenChildDispatches_dispatchCorrect(
            false
        )
    }

    @Test
    fun isEmpty_nothingAdded_returnsTrue() {
        assertThat(hitPathTracker.isEmpty()).isTrue()
    }

    @Test
    fun isEmpty_oneNodeAdded_returnsFalse() {
        hitPathTracker.addHitPath(PointerId(0), listOf(PointerInputFilterMock()))
        assertThat(hitPathTracker.isEmpty()).isFalse()
    }

    @Test
    fun isEmpty_oneNodeAddedThenRemoved_returnsTrue() {
        hitPathTracker.addHitPath(PointerId(0), listOf(PointerInputFilterMock()))
        hitPathTracker.removeHitPath(PointerId(0))
        assertThat(hitPathTracker.isEmpty()).isTrue()
    }

    private fun dispatchCustomEvent_1Parent2ChildrenChildDispatches_dispatchCorrect(
        firstChildDispatches: Boolean
    ) {
        // Arrange

        val parentPif = PointerInputFilterMock()
        lateinit var childPif1: PointerInputFilter
        lateinit var childPif2: PointerInputFilter

        lateinit var dispatcher: CustomEventDispatcher
        val initHandler: (CustomEventDispatcher) -> Unit = { dispatcher = it }

        if (firstChildDispatches) {
            childPif1 = PointerInputFilterMock(
                initHandler = initHandler
            )
            childPif2 = PointerInputFilterMock()
        } else {
            childPif1 = PointerInputFilterMock()
            childPif2 = PointerInputFilterMock(
                initHandler = initHandler
            )
        }

        hitPathTracker.addHitPath(PointerId(3), listOf(parentPif, childPif1))
        hitPathTracker.addHitPath(PointerId(4), listOf(parentPif, childPif2))

        val event = TestCustomEvent("test")

        // Act

        dispatcher.dispatchCustomEvent(event)

        // Assert
        inOrder(parentPif) {
            verify(parentPif).onCustomEvent(event, PointerEventPass.InitialDown)
            verify(parentPif).onCustomEvent(event, PointerEventPass.PreUp)
            verify(parentPif).onCustomEvent(event, PointerEventPass.PreDown)
            verify(parentPif).onCustomEvent(event, PointerEventPass.PostUp)
            verify(parentPif).onCustomEvent(event, PointerEventPass.PostDown)
        }
        verify(childPif1, never()).onCustomEvent(any(), any())
        verify(childPif1, never()).onCustomEvent(any(), any())
    }

    private fun areEqual(actualNode: NodeParent, expectedNode: NodeParent): Boolean {
        var check = true

        if (actualNode.children.size != expectedNode.children.size) {
            return false
        }
        for (child in actualNode.children) {
            check = check && expectedNode.children.any {
                areEqual(child, it)
            }
        }

        return check
    }

    private fun areEqual(actualNode: Node, expectedNode: Node): Boolean {
        if (actualNode.pointerInputFilter !== expectedNode.pointerInputFilter) {
            return false
        }

        if (actualNode.pointerIds.size != expectedNode.pointerIds.size) {
            return false
        }
        var check = true
        actualNode.pointerIds.forEach {
            check = check && expectedNode.pointerIds.contains(it)
        }
        if (!check) {
            return false
        }

        if (actualNode.children.size != expectedNode.children.size) {
            return false
        }
        for (child in actualNode.children) {
            check = check && expectedNode.children.any {
                areEqual(child, it)
            }
        }

        return check
    }
}

fun PointerInputFilterMock(
    initHandler: (CustomEventDispatcher) -> Unit = mock(),
    pointerInputHandler: PointerInputHandler = spy(StubPointerInputHandler()),
    isAttached: Boolean = true
): PointerInputFilter =
    spy(
        PointerInputFilterStub(
            pointerInputHandler,
            initHandler
        ).apply {
            layoutCoordinates = LayoutCoordinatesStub(isAttached)
        }
    )

open class PointerInputFilterStub(
    val pointerInputHandler: PointerInputHandler,
    val initHandler: (CustomEventDispatcher) -> Unit
) : PointerInputFilter() {

    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntPxSize
    ): List<PointerInputChange> {
        return pointerInputHandler(changes, pass, bounds)
    }

    override fun onCancel() {}

    override fun onInit(customEventDispatcher: CustomEventDispatcher) {
        initHandler(customEventDispatcher)
    }

    override fun onCustomEvent(customEvent: CustomEvent, pass: PointerEventPass) {}
}

internal data class TestCustomEvent(val value: String) : CustomEvent

class LayoutCoordinatesStub(
    override val isAttached: Boolean = true
) : LayoutCoordinates {

    override val size: IntPxSize
        get() = IntPxSize(IntPx.Infinity, IntPx.Infinity)

    override val providedAlignmentLines: Set<AlignmentLine>
        get() = TODO("not implemented")

    override val parentCoordinates: LayoutCoordinates?
        get() = TODO("not implemented")

    override fun globalToLocal(global: PxPosition): PxPosition {
        TODO("not implemented")
    }

    override fun localToGlobal(local: PxPosition): PxPosition {
        return local
    }

    override fun localToRoot(local: PxPosition): PxPosition {
        TODO("not implemented")
    }

    override fun childToLocal(child: LayoutCoordinates, childLocal: PxPosition): PxPosition {
        TODO("not implemented")
    }

    override fun childBoundingBox(child: LayoutCoordinates): PxBounds {
        TODO("not implemented")
    }

    override fun get(line: AlignmentLine): IntPx? {
        TODO("not implemented")
    }
}