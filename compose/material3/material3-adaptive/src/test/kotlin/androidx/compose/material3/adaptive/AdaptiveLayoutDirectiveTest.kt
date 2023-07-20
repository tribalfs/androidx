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

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(JUnit4::class)
class AdaptiveLayoutDirectiveTest {
    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_compactWidth() {
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(1)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(16.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(0.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(16.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_mediumWidth() {
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(750.dp, 900.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(1)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(0.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_expandedWidth() {
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(1200.dp, 800.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(2)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_tabletop() {
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(isTabletop = true)
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(1)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(2)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(0.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(24.dp)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_compactWidth() {
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(1)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(16.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(0.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(16.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_mediumWidth() {
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(750.dp, 900.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(2)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_expandedWidth() {
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(1200.dp, 800.dp)),
                Posture()
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(2)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(1)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(0.dp)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_tabletop() {
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(isTabletop = true)
            )
        )

        assertThat(layoutDirective.maxHorizontalPartitions).isEqualTo(2)
        assertThat(layoutDirective.maxVerticalPartitions).isEqualTo(2)
        assertThat(layoutDirective.gutterSizes.outerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerVertical).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.outerHorizontal).isEqualTo(24.dp)
        assertThat(layoutDirective.gutterSizes.innerHorizontal).isEqualTo(24.dp)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_alwaysAvoidHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.AlwaysAvoid
        )

        assertThat(layoutDirective.excludedBounds).isEqualTo(allHingeBounds)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_avoidOccludingHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.AvoidOccluding
        )

        assertThat(layoutDirective.excludedBounds).isEqualTo(occludingHingeBounds)
    }

    @Test
    fun test_calculateStandardAdaptiveLayoutDirective_neverAvoidHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateStandardAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.NeverAvoid
        )

        assertThat(layoutDirective.excludedBounds).isEmpty()
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_alwaysAvoidHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.AlwaysAvoid
        )

        assertThat(layoutDirective.excludedBounds).isEqualTo(allHingeBounds)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_avoidOccludingHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.AvoidOccluding
        )

        assertThat(layoutDirective.excludedBounds).isEqualTo(occludingHingeBounds)
    }

    @Test
    fun test_calculateDenseAdaptiveLayoutDirective_neverAvoidHinge() {
        val occludingHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
        )
        val allHingeBounds = listOf(
            Rect(0F, 0F, 1F, 1F),
            Rect(1F, 1F, 2F, 2F),
            Rect(2F, 2F, 3F, 3F)
        )
        val layoutDirective = calculateDenseAdaptiveLayoutDirective(
            WindowAdaptiveInfo(
                WindowSizeClass.calculateFromSize(DpSize(700.dp, 800.dp)),
                Posture(
                    allHingeBounds = allHingeBounds,
                    occludingHingeBounds = occludingHingeBounds
                )
            ),
            HingePolicy.NeverAvoid
        )

        assertThat(layoutDirective.excludedBounds).isEmpty()
    }
}
