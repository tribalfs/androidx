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

package androidx.compose.ui.layout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class LayoutDensityTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun layoutReadsCompositionLocalDensity() {
        var localDensity by mutableStateOf(5f)
        var localFontScale by mutableStateOf(7f)

        var measureScopeDensity = 0f
        var measureScopeFontScale = 0f
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(localDensity, localFontScale)) {
                Layout({}) { _, _ ->
                    measureScopeDensity = density
                    measureScopeFontScale = fontScale
                    layout(0, 0) {}
                }
            }
        }

        rule.runOnIdle {
            Assert.assertEquals(localDensity, measureScopeDensity)
            Assert.assertEquals(localFontScale, measureScopeFontScale)
            localDensity = 9f
            localFontScale = 11f
        }

        rule.runOnIdle {
            Assert.assertEquals(localDensity, measureScopeDensity)
            Assert.assertEquals(localFontScale, measureScopeFontScale)
        }
    }
}
