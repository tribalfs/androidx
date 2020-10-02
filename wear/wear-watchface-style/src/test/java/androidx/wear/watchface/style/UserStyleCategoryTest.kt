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

package androidx.wear.watchface.style

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(StyleTestRunner::class)
class UserStyleCategoryTest {

    @Test
    fun rangedUserStyleCategory_getOptionForId_returns_default_for_bad_input() {
        val defaultValue = 0.75
        val rangedUserStyleCategory =
            DoubleRangeUserStyleCategory(
                "example_category",
                "Example Ranged Category",
                "An example category",
                null,
                0.0,
                1.0,
                defaultValue,
                listOf(Layer.BASE_LAYER)
            )

        assertThat(rangedUserStyleCategory.getOptionForId("not a number").id)
            .isEqualTo(defaultValue.toString())

        assertThat(rangedUserStyleCategory.getOptionForId("-1").id)
            .isEqualTo(defaultValue.toString())

        assertThat(rangedUserStyleCategory.getOptionForId("10").id)
            .isEqualTo(defaultValue.toString())
    }

    @Test
    fun rangedUserStyleCategory_getOptionForId() {
        val defaultValue = 0.75
        val rangedUserStyleCategory =
            DoubleRangeUserStyleCategory(
                "example_category",
                "Example Ranged Category",
                "An example category",
                null,
                0.0,
                1.0,
                defaultValue,
                listOf(Layer.BASE_LAYER)
            )

        assertThat(rangedUserStyleCategory.getOptionForId("0").id)
            .isEqualTo("0.0")

        assertThat(rangedUserStyleCategory.getOptionForId("0.5").id)
            .isEqualTo("0.5")

        assertThat(rangedUserStyleCategory.getOptionForId("1").id)
            .isEqualTo("1.0")
    }
}