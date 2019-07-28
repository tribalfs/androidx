/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.compose.benchmark

import android.app.Activity
import androidx.compose.Composable
import androidx.compose.Composer
import androidx.compose.FrameManager
import androidx.compose.benchmark.dbmonster.DatabaseList
import androidx.compose.benchmark.dbmonster.DatabaseRow
import androidx.compose.benchmark.dbmonster.Table
import androidx.compose.composer
import androidx.compose.disposeComposition
import androidx.compose.runWithCurrent
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.setContent
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlin.random.Random

/**
 * This is an implementation of a classic web perf benchmark "dbmonster". This can provide insight into apps with
 * lots of updating parts at once. It may also be good tests for the Text and Layout stacks of compose UI.
 *
 * See: http://mathieuancelin.github.io/js-repaint-perfs/
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DbMonsterBenchmark : ComposeBenchmarkBase() {

    @UiThreadTest
    @Test
    fun dbMonster_count10_mutate10() = dbMonsterBenchmark(count = 10, mutate = 10)

    @UiThreadTest
    @Test
    fun dbMonster_count20_mutate01() = dbMonsterBenchmark(count = 20, mutate = 1)

    /**
     * @param count - the number of databases (2x this will be number of rows)
     * @param mutate - the number of databases to mutate/update on each frame (2x count will be 100%)
     */
    private fun dbMonsterBenchmark(count: Int, mutate: Int) {
        val random = Random(0)
        println(count)
        println(mutate)
        println(random)
        val list = DatabaseList(count, random)
        measureRecompose {
            compose {
                Table {
                    for (db in list.databases) {
                        DatabaseRow(db = db)
                    }
                }
            }
            update {
                list.update(mutate)
            }
        }
    }
}
