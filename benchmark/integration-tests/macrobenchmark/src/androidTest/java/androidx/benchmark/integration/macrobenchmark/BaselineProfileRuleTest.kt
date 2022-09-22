/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.benchmark.integration.macrobenchmark

import android.content.Intent
import androidx.benchmark.Outputs
import androidx.benchmark.Shell
import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@LargeTest
@SdkSuppress(minSdkVersion = 29)
@OptIn(ExperimentalBaselineProfilesApi::class)
class BaselineProfileRuleTest {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun appNotInstalled() {
        val error = assertFailsWith<AssertionError> {
            baselineRule.collectBaselineProfile(
                packageName = "fake.package.not.installed",
                profileBlock = {
                    fail("not expected")
                }
            )
        }
        println(error.message)
        assertTrue(error.message!!.contains("Unable to find target package"))
    }

    @Test
    fun filter() {
        assumeTrue(Shell.isSessionRooted())

        var nextIteration = 1
        // Collects the baseline profile
        baselineRule.collectBaselineProfile(
            packageName = PACKAGE_NAME,
            packageFilters = listOf(PACKAGE_NAME),
            profileBlock = {
                assertEquals(nextIteration++, iteration)
                startActivityAndWait(Intent(ACTION))
                device.waitForIdle()
            }
        )
        assertEquals(4, nextIteration)

        // Asserts the output of the baseline profile
        val lines = File(Outputs.outputDirectory, BASELINE_PROFILE_OUTPUT_FILE_NAME).readLines()
        assertThat(lines).containsExactly(
            "HSPLandroidx/benchmark/integration/macrobenchmark/target/EmptyActivity;" +
                "-><init>()V",
            "HSPLandroidx/benchmark/integration/macrobenchmark/target/EmptyActivity;" +
                "->onCreate(Landroid/os/Bundle;)V",
            "Landroidx/benchmark/integration/macrobenchmark/target/EmptyActivity;",
        )
    }

    companion object {
        private const val PACKAGE_NAME =
            "androidx.benchmark.integration.macrobenchmark.target"
        private const val ACTION =
            "androidx.benchmark.integration.macrobenchmark.target.EMPTY_ACTIVITY"

        // Note: this name is automatically generated starting from class and method name,
        // according to the patter `<class>_<method>-baseline-prof.txt`. Changes for class and
        // method names should be reflected here in order for the test to succeed.
        private const val BASELINE_PROFILE_OUTPUT_FILE_NAME =
            "BaselineProfileRuleTest_filter-baseline-prof.txt"
    }
}
