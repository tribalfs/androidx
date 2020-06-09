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

import androidx.test.filters.SmallTest
import androidx.ui.core.LayoutNode.LayoutState
import androidx.ui.core.test.AndroidOwnerExtraAssertionsRule
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.max

@SmallTest
@RunWith(JUnit4::class)
class MeasureAndLayoutDelegateTest {

    private val Size = 100
    private val DifferentSize = 50
    private val DifferentSize2 = 30

    @get:Rule
    val excessiveAssertions = AndroidOwnerExtraAssertionsRule()

    @Test
    fun requiresMeasureWhenJustCreated() {
        val root = root {
            add(node())
        }

        createDelegate(root, firstMeasureCompleted = false)

        assertMeasureRequired(root)
        assertMeasureRequired(root.first)
    }

    @Test
    fun measureNotRequiredAfterFirstMeasure() {
        val root = root {
            add(node())
        }

        createDelegate(root)

        assertMeasuredAndLaidOut(root)
        assertMeasuredAndLaidOut(root.first)
    }

    @Test
    fun relayoutNotRequiredAfterFirstMeasure() {
        val root = root {
            add(node())
        }

        createDelegate(root)

        assertMeasuredAndLaidOut(root)
        assertMeasuredAndLaidOut(root.first)
    }

    @Test
    fun measuredAndLaidOutAfterFirstMeasureAndLayout() {
        val root = root {
            add(node())
        }

        assertRemeasured(root) {
            assertRemeasured(root.first) {
                createDelegate(root)
            }
        }
    }

    // remeasure request:

    @Test
    fun childRemeasureRequest_remeasureRequired() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        delegate.requestRemeasure(root.first)
        assertMeasureRequired(root.first)
    }

    @Test
    fun childRemeasureRequest_childRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childMeasuredInLayoutBlockRemeasureRequest_childRemeasured() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureWithTheSameResult_parentNotRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureWithDifferentResult_parentRemeasured() {
        val root = root {
            wrapChildren = true
            add(node {
                size = DifferentSize
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertRemeasured(root) {
                root.first.size = DifferentSize2
                delegate.requestRemeasure(root.first)
                assertThat(delegate.measureAndLayout()).isTrue()
            }
        }
    }

    @Test
    fun childRemeasureInLayoutBlockWithTheSameResult_parentNotRemeasured() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureInLayoutBlockWithDifferentResult_parentNotRemeasured() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            root.first.size = DifferentSize
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureRequest_childRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childMeasuredInLayoutBlockRelayoutRequest_childRelaidOut() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureWithTheSameResult_parentNotRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureInLayoutBlockWithTheSameResult_parentNotRelaidOut() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureInLayoutBlockWithDifferentResult_parentRelaidOut() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root) {
            root.first.size = DifferentSize
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun rootRemeasureRequest_childNotAffected() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertNotRemeasured(root.first) {
                assertNotRelaidOut(root.first) {
                    delegate.requestRemeasure(root)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun parentRemeasureRequest_childNotAffected() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertNotRemeasured(root.first.first) {
                assertNotRelaidOut(root.first.first) {
                    delegate.requestRemeasure(root.first)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    // relayout request:

    @Test
    fun childRelayoutRequest_childRelayoutRequired() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        delegate.requestRelayout(root.first)
        assertLayoutRequired(root.first)
    }

    @Test
    fun childRelayoutRequest_childRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRelayoutRequest_childNotRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root.first) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRelayoutRequest_parentNotRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRelayoutRequest_parentNotRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childMeasuredInLayoutBlockRelayoutRequest_parentNotRemeasured() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childMeasuredInLayoutBlockRelayoutRequest_parentNotRelaidOut() {
        val root = root {
            add(node())
            measureInLayoutBlock()
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun rootRelayoutRequest_childNotAffected() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root) {
            assertNotRelaidOut(root.first) {
                delegate.requestRelayout(root)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun parentRelayoutRequest_childNotAffected() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            assertNotRelaidOut(root.first.first) {
                delegate.requestRelayout(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    // request twice

    @Test
    fun childRemeasureRequestedTwice_childRemeasuredOnce() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            delegate.requestRemeasure(root.first)
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureRequestedTwice_childRelaidOutOnce() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRemeasure(root.first)
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureAndRelayoutRequested_childRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            delegate.requestRemeasure(root.first)
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRemeasureAndRelayoutRequested_childRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRemeasure(root.first)
            delegate.requestRelayout(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRelayoutAndRemeasureRequested_childRemeasured() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            delegate.requestRelayout(root.first)
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun childRelayoutAndRemeasureRequested_childRelaidOut() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            delegate.requestRelayout(root.first)
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    // Siblings

    @Test
    fun firstChildRemeasureRequest_onlyFirstChildRemeasured() {
        val root = root {
            add(node())
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertNotRemeasured(root.second) {
                delegate.requestRemeasure(root.first)
                delegate.measureAndLayout()
            }
        }
    }

    @Test
    fun firstChildRelayoutRequest_onlyFirstChildRelaid() {
        val root = root {
            add(node())
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            assertNotRelaidOut(root.second) {
                delegate.requestRelayout(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun bothChildrenRemeasureRequest_bothRemeasured() {
        val root = root {
            add(node())
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertRemeasured(root.second) {
                delegate.requestRemeasure(root.first)
                delegate.requestRemeasure(root.second)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun bothChildrenRelayoutRequest_bothRelaidOut() {
        val root = root {
            add(node())
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root.first) {
            assertRelaidOut(root.second) {
                delegate.requestRelayout(root.first)
                delegate.requestRelayout(root.second)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun oneChildRelayoutRequestAnotherRemeasure() {
        val root = root {
            add(node())
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertNotRemeasured(root.second) {
                assertRelaidOut(root.second) {
                    delegate.requestRemeasure(root.first)
                    delegate.requestRelayout(root.second)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    // different levels

    @Test
    fun remeasureTwoNodesOnDifferentLayers_othersAreNotAffected() {
        val root = root {
            add(node())
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            assertRemeasured(root.first) {
                assertNotRemeasured(root.second) {
                    assertRemeasured(root.second.first) {
                        delegate.requestRemeasure(root.first)
                        delegate.requestRemeasure(root.second.first)
                        assertThat(delegate.measureAndLayout()).isFalse()
                    }
                }
            }
        }
    }

    @Test
    fun changeSizeOfTheLeaf_remeasuresUpToTheFixedSizeParent() {
        val root = root {
            wrapChildren = true
            add(node {
                size = DifferentSize
                add(node {
                    wrapChildren = true
                    add(node {
                        size = DifferentSize
                    })
                })
            })
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            assertRemeasured(root.first) {
                assertRemeasured(root.first.first) {
                    val leaf = root.first.first.first
                    assertRemeasured(leaf) {
                        leaf.size = DifferentSize2
                        delegate.requestRemeasure(leaf)
                        assertThat(delegate.measureAndLayout()).isFalse()
                    }
                }
            }
        }
    }

    @Test
    fun remeasureRequestForItemsOnTheSameLevelButDifferentParents() {
        val root = root {
            add(node {
                add(node())
            })
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            assertNotRemeasured(root.first) {
                assertRemeasured(root.first.first) {
                    assertNotRemeasured(root.second) {
                        assertRemeasured(root.second.first) {
                            delegate.requestRemeasure(root.first.first)
                            delegate.requestRemeasure(root.second.first)
                            assertThat(delegate.measureAndLayout()).isFalse()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun relayoutRequestForItemsOnTheSameLevelButDifferentParents() {
        val root = root {
            add(node {
                add(node())
            })
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            assertNotRelaidOut(root.first) {
                assertRelaidOut(root.first.first) {
                    assertNotRelaidOut(root.second) {
                        assertRelaidOut(root.second.first) {
                            delegate.requestRelayout(root.first.first)
                            delegate.requestRelayout(root.second.first)
                            assertThat(delegate.measureAndLayout()).isFalse()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun relayoutAndRemeasureRequestForItemsOnTheSameLevelButDifferentParents() {
        val root = root {
            add(node {
                add(node())
            })
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertNotRelaidOut(root) {
            assertNotRelaidOut(root.first) {
                assertRemeasured(root.first.first) {
                    assertNotRelaidOut(root.second) {
                        assertRelaidOut(root.second.first) {
                            delegate.requestRemeasure(root.first.first)
                            delegate.requestRelayout(root.second.first)
                            assertThat(delegate.measureAndLayout()).isFalse()
                        }
                    }
                }
            }
        }
    }

    // request during measure

    @Test
    fun requestChildRemeasureDuringMeasure() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertRemeasured(root.first) {
                root.runDuringMeasure {
                    delegate.requestRemeasure(root.first)
                }
                delegate.requestRemeasure(root)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun requestGrandchildRemeasureDuringMeasure() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertNotRemeasured(root.first) {
                assertRemeasured(root.first.first) {
                    root.runDuringMeasure {
                        delegate.requestRemeasure(root.first.first)
                    }
                    delegate.requestRemeasure(root)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun requestChildRelayoutDuringMeasure() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertRelaidOut(root.first) {
                root.runDuringMeasure {
                    delegate.requestRelayout(root.first)
                }
                delegate.requestRemeasure(root)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun requestGrandchildRelayoutDuringMeasure() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertNotRelaidOut(root.first) {
                assertRelaidOut(root.first.first) {
                    root.runDuringMeasure {
                        delegate.requestRelayout(root.first.first)
                    }
                    delegate.requestRemeasure(root)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun requestChildRemeasureDuringParentLayout() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root) {
            assertRemeasured(root.first) {
                root.runDuringLayout {
                    delegate.requestRemeasure(root.first)
                }
                delegate.requestRelayout(root)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun requestGrandchildRemeasureDuringParentLayout() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRelaidOut(root) {
            assertNotRelaidOut(root.first) {
                assertRemeasured(root.first.first) {
                    root.runDuringLayout {
                        delegate.requestRemeasure(root.first.first)
                    }
                    delegate.requestRelayout(root)
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun requestRemeasureForCurrentlyBeingRemeasuredNode() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            root.runDuringMeasure {
                delegate.requestRemeasure(root.first)
            }
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun requestRelayoutForCurrentlyBeingRemeasuredNode() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertRelaidOut(root.first) {
                root.runDuringMeasure {
                    delegate.requestRelayout(root.first)
                }
                delegate.requestRemeasure(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun requestRemeasureForCurrentlyBeingRelayoutNode() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertRelaidOut(root.first, times = 2) {
                root.first.runDuringLayout {
                    delegate.requestRemeasure(root.first)
                }
                delegate.requestRelayout(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    @Test
    fun requestRelayoutForCurrentlyBeingRelayoutNode() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root.first) {
            assertRelaidOut(root.first) {
                root.runDuringLayout {
                    delegate.requestRelayout(root.first)
                }
                delegate.requestRelayout(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    // Rtl

    @Test
    fun changeDirectionForChildren() {
        val root = root {
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root, withDirection = LayoutDirection.Ltr) {
            assertRemeasured(root.first, withDirection = LayoutDirection.Rtl) {
                root.childrenDirection = LayoutDirection.Rtl
                delegate.requestRemeasure(root)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    // Updating root constraints / layout direction

    @Test
    fun changingParentParamsToTheSameValue_noRemeasures() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            assertNotRemeasured(root.first) {
                assertNotRemeasured(root.first.first) {
                    delegate.updateRootParams(
                        defaultRootConstraints(),
                        LayoutDirection.Ltr
                    )
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun changingParentConstraints_remeasureSubTree() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertRemeasured(root.first) {
                assertRemeasured(root.first.first) {
                    delegate.updateRootParams(
                        Constraints(maxWidth = DifferentSize, maxHeight = DifferentSize),
                        LayoutDirection.Ltr
                    )
                    assertThat(delegate.measureAndLayout()).isTrue()
                }
            }
        }
    }

    @Test
    fun changingParentConstraints_remeasureOnlyAffectedNodes() {
        val root = root {
            add(node {
                size = DifferentSize2
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root.first) {
            assertNotRemeasured(root.first.first) {
                delegate.updateRootParams(
                    Constraints(maxWidth = DifferentSize, maxHeight = DifferentSize),
                    LayoutDirection.Ltr
                )
                assertThat(delegate.measureAndLayout()).isTrue()
            }
        }
    }

    @Test
    fun changingParentDirection_remeasureSubTree() {
        val root = root {
            add(node {
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root, withDirection = LayoutDirection.Rtl) {
            assertRemeasured(root.first, withDirection = LayoutDirection.Rtl) {
                assertRemeasured(root.first.first, withDirection = LayoutDirection.Rtl) {
                    delegate.updateRootParams(
                        defaultRootConstraints(),
                        LayoutDirection.Rtl
                    )
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun changingParentDirection_remeasureOnlyAffectedNodes() {
        val root = root {
            add(node {
                childrenDirection = LayoutDirection.Rtl
                add(node())
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(root, withDirection = LayoutDirection.Rtl) {
            assertRemeasured(root.first, withDirection = LayoutDirection.Rtl) {
                assertNotRemeasured(root.first.first) {
                    delegate.updateRootParams(
                        defaultRootConstraints(),
                        LayoutDirection.Rtl
                    )
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    // LayoutModifier

    @Test
    fun requestRemeasureTriggersModifierRemeasure() {
        val spyModifier = SpyLayoutModifier()
        val root = root {
            add(node {
                modifier = spyModifier
            })
        }

        val delegate = createDelegate(root)

        assertRemeasured(spyModifier) {
            delegate.requestRemeasure(root.first)
            assertThat(delegate.measureAndLayout()).isFalse()
        }
    }

    @Test
    fun requestRelayoutTriggersModifierRelayout() {
        val spyModifier = SpyLayoutModifier()
        val root = root {
            add(node {
                modifier = spyModifier
            })
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(spyModifier) {
            assertRelaidOut(spyModifier) {
                delegate.requestRelayout(root.first)
                assertThat(delegate.measureAndLayout()).isFalse()
            }
        }
    }

    // Relayout depending on the measured child
    // Illustrates the case when we run layoutChildren() on the parent node, but some of its
    // children are not yet measured even if they are supposed to be measured in the measure
    // block of our parent.
    //
    // Example:
    // val child = Layout(...)
    // Layout(child) { measuruables, constraints ->
    //    val placeable = measurables.first().measure(constraints)
    //    layout(placeable.width, placeable.height) {
    //       placeable.place(0, 0)
    //    }
    // }
    // Then some changes scheduled remeasure for child and relayout for parent.
    // During the measureAndLayout() we will start with the parent as it has lower depth.
    // Inside the layout block we will call placeable.width which is currently dirty as the child
    // was scheduled to remeasure.

    @Test
    fun relayoutDependingOnRemeasuredChild() {
        val root = root {
            // this node will be measured in the measuring block
            add(node())
        }

        val delegate = createDelegate(root)

        assertNotRemeasured(root) {
            assertRelaidOut(root) {
                assertRemeasured(root.first) {
                    delegate.requestRemeasure(root.first)
                    delegate.requestRelayout(root)
                    root.runDuringLayout {
                        // this means the root.first will be measured before laying out the root
                        assertThat(root.first.layoutState).isEqualTo(LayoutState.NeedsRelayout)
                    }
                    assertThat(delegate.measureAndLayout()).isFalse()
                }
            }
        }
    }

    @Test
    fun relayoutDependingOnRemeasuredChild_parentRemeasuredBecauseOfChangedSize() {
        val root = root {
            wrapChildren = true
            // this node will be measured in the measuring block
            add(node())
        }

        val delegate = createDelegate(root)

        assertRemeasured(root) {
            assertRemeasured(root.first) {
                root.first.size = DifferentSize
                delegate.requestRemeasure(root.first)
                delegate.requestRelayout(root)
                assertThat(delegate.measureAndLayout()).isTrue()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDelegate(
        root: LayoutNode,
        firstMeasureCompleted: Boolean = true
    ): MeasureAndLayoutDelegate {
        val delegate = MeasureAndLayoutDelegate(root)
        root.attach(mock {
            on { measureIteration } doAnswer {
                delegate.measureIteration
            }
            on { onRequestMeasure(any()) } doAnswer {
                delegate.requestRemeasure(it.arguments[0] as LayoutNode)
                Unit
            }
            on { observeMeasureModelReads(any(), any()) } doAnswer {
                (it.arguments[1] as () -> Unit).invoke()
            }
            on { observeLayoutModelReads(any(), any()) } doAnswer {
                (it.arguments[1] as () -> Unit).invoke()
            }
        })
        if (firstMeasureCompleted) {
            delegate.updateRootParams(
                defaultRootConstraints(),
                LayoutDirection.Ltr
            )
            assertThat(delegate.measureAndLayout()).isTrue()
        }
        return delegate
    }

    private fun defaultRootConstraints() = Constraints(maxWidth = Size, maxHeight = Size)

    private fun assertNotRemeasured(node: LayoutNode, block: (LayoutNode) -> Unit) {
        val measuresCountBefore = node.measuresCount
        block(node)
        assertThat(node.measuresCount).isEqualTo(measuresCountBefore)
        assertMeasuredAndLaidOut(node)
    }

    private fun assertRemeasured(
        node: LayoutNode,
        times: Int = 1,
        withDirection: LayoutDirection? = null,
        block: (LayoutNode) -> Unit
    ) {
        val measuresCountBefore = node.measuresCount
        block(node)
        assertThat(node.measuresCount).isEqualTo(measuresCountBefore + times)
        if (withDirection != null) {
            assertThat(node.measuredWithLayoutDirection).isEqualTo(withDirection)
        }
        assertMeasuredAndLaidOut(node)
    }

    private fun assertRelaidOut(node: LayoutNode, times: Int = 1, block: (LayoutNode) -> Unit) {
        val layoutsCountBefore = node.layoutsCount
        block(node)
        assertThat(node.layoutsCount).isEqualTo(layoutsCountBefore + times)
        assertMeasuredAndLaidOut(node)
    }

    private fun assertNotRelaidOut(node: LayoutNode, block: (LayoutNode) -> Unit) {
        val layoutsCountBefore = node.layoutsCount
        block(node)
        assertThat(node.layoutsCount).isEqualTo(layoutsCountBefore)
        assertMeasuredAndLaidOut(node)
    }

    private fun assertMeasureRequired(node: LayoutNode) {
        assertThat(node.layoutState).isEqualTo(LayoutState.NeedsRemeasure)
    }

    private fun assertMeasuredAndLaidOut(node: LayoutNode) {
        assertThat(node.layoutState).isEqualTo(LayoutState.Ready)
    }

    private fun assertLayoutRequired(node: LayoutNode) {
        assertThat(node.layoutState).isEqualTo(LayoutState.NeedsRelayout)
    }

    private fun assertRemeasured(
        modifier: SpyLayoutModifier,
        block: () -> Unit
    ) {
        val measuresCountBefore = modifier.measuresCount
        block()
        assertThat(modifier.measuresCount).isEqualTo(measuresCountBefore + 1)
    }

    private fun assertNotRemeasured(
        modifier: SpyLayoutModifier,
        block: () -> Unit
    ) {
        val measuresCountBefore = modifier.measuresCount
        block()
        assertThat(modifier.measuresCount).isEqualTo(measuresCountBefore)
    }

    private fun assertRelaidOut(
        modifier: SpyLayoutModifier,
        block: () -> Unit
    ) {
        val layoutsCountBefore = modifier.layoutsCount
        block()
        assertThat(modifier.layoutsCount).isEqualTo(layoutsCountBefore + 1)
    }

    private fun root(block: LayoutNode.() -> Unit = {}): LayoutNode {
        return node(block).apply {
            isPlaced = true
        }
    }

    private fun node(block: LayoutNode.() -> Unit = {}): LayoutNode {
        return LayoutNode().apply {
            measureBlocks = MeasureInMeasureBlock()
            block.invoke(this)
        }
    }
}

private fun LayoutNode.add(child: LayoutNode) = insertAt(children.count(), child)
private fun LayoutNode.measureInLayoutBlock() {
    measureBlocks = MeasureInLayoutBlock()
}

private fun LayoutNode.runDuringMeasure(block: () -> Unit) {
    (measureBlocks as SmartMeasureBlock).preMeasureCallback = block
}

private fun LayoutNode.runDuringLayout(block: () -> Unit) {
    (measureBlocks as SmartMeasureBlock).preLayoutCallback = block
}

private val LayoutNode.first: LayoutNode get() = children.first()
private val LayoutNode.second: LayoutNode get() = children[1]
private val LayoutNode.measuresCount: Int
    get() = (measureBlocks as SmartMeasureBlock).measuresCount
private val LayoutNode.layoutsCount: Int
    get() = (measureBlocks as SmartMeasureBlock).layoutsCount
private var LayoutNode.wrapChildren: Boolean
    get() = (measureBlocks as SmartMeasureBlock).wrapChildren
    set(value) {
        (measureBlocks as SmartMeasureBlock).wrapChildren = value
    }
private val LayoutNode.measuredWithLayoutDirection: LayoutDirection
    get() = (measureBlocks as SmartMeasureBlock).measuredLayoutDirection!!
private var LayoutNode.size: Int?
    get() = (measureBlocks as SmartMeasureBlock).size
    set(value) {
        (measureBlocks as SmartMeasureBlock).size = value
    }
private var LayoutNode.childrenDirection: LayoutDirection?
    get() = (measureBlocks as SmartMeasureBlock).childrenLayoutDirection
    set(value) {
        (measureBlocks as SmartMeasureBlock).childrenLayoutDirection = value
    }

abstract class SmartMeasureBlock : LayoutNode.NoIntrinsicsMeasureBlocks("") {
    var measuresCount = 0
        protected set
    var layoutsCount = 0
        protected set
    open var wrapChildren = false
    var preMeasureCallback: (() -> Unit)? = null
    var preLayoutCallback: (() -> Unit)? = null
    var measuredLayoutDirection: LayoutDirection? = null
        protected set
    var childrenLayoutDirection: LayoutDirection? = null
    // child size is used when null
    var size: Int? = null
}

private class MeasureInMeasureBlock : SmartMeasureBlock() {
    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        measuresCount++
        measuredLayoutDirection = layoutDirection
        preMeasureCallback?.invoke()
        preMeasureCallback = null
        val childConstraints = if (size == null) {
            constraints
        } else {
            val size = size!!
            constraints.copy(maxWidth = size, maxHeight = size)
        }
        val placeables = measurables.map {
            it.measure(childConstraints, childrenLayoutDirection ?: layoutDirection)
        }
        var maxWidth = 0
        var maxHeight = 0
        if (!wrapChildren) {
            maxWidth = childConstraints.maxWidth
            maxHeight = childConstraints.maxHeight
        } else {
            placeables.forEach { placeable ->
                maxWidth = max(placeable.width, maxWidth)
                maxHeight = max(placeable.height, maxHeight)
            }
        }
        return measureScope.layout(maxWidth, maxHeight) {
            layoutsCount++
            preLayoutCallback?.invoke()
            preLayoutCallback = null
            placeables.forEach { placeable ->
                placeable.place(0, 0)
            }
        }
    }
}

private class MeasureInLayoutBlock : SmartMeasureBlock() {

    override var wrapChildren: Boolean
        get() = false
        set(value) {
            if (value) {
                throw IllegalArgumentException("MeasureInLayoutBlock always fills the parent size")
            }
        }

    override fun measure(
        measureScope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        measuresCount++
        measuredLayoutDirection = layoutDirection
        preMeasureCallback?.invoke()
        preMeasureCallback = null
        val childConstraints = if (size == null) {
            constraints
        } else {
            val size = size!!
            constraints.copy(maxWidth = size, maxHeight = size)
        }
        return measureScope.layout(childConstraints.maxWidth, childConstraints.maxHeight) {
            preLayoutCallback?.invoke()
            preLayoutCallback = null
            layoutsCount++
            measurables.forEach {
                it.measure(childConstraints, childrenLayoutDirection ?: layoutDirection)
                    .place(0, 0)
            }
        }
    }
}

private class SpyLayoutModifier : LayoutModifier {
    var measuresCount = 0
    var layoutsCount = 0

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): MeasureScope.MeasureResult {
        measuresCount++
        return layout(constraints.maxWidth, constraints.maxHeight) {
            layoutsCount++
            measurable.measure(constraints).place(0, 0)
        }
    }
}