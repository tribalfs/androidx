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

package androidx.health.connect.client.units

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MassTest {

    @Rule @JvmField val expect = Expect.create()

    @Test
    fun equals_sameValues_areEqual() {
        expect.that(Mass.kilograms(107.0)).isEqualTo(Mass.kilograms(107.0))
        expect.that(Mass.kilograms(107.0)).isEqualTo(Mass.grams(107_000.0))
    }

    @Test
    fun equals_differentValues_areNotEqual() {
        expect.that(Mass.kilograms(107.1)).isNotEqualTo(Mass.kilograms(107.0))
        expect.that(Mass.kilograms(107.1)).isNotEqualTo(Mass.grams(107_000.0))
    }

    @Test
    fun hashCode_sameValues_areEqual() {
        expect.that(Mass.kilograms(107.0).hashCode()).isEqualTo(Mass.grams(107_000.0).hashCode())
    }

    @Test
    fun hashCode_differentValues_areNotEqual() {
        expect.that(Mass.kilograms(107.1).hashCode()).isNotEqualTo(Mass.grams(107_000.0).hashCode())
    }
}
