/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.benchmark.macro

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@LargeTest
class MacrobenchmarkScopeTest {
    @Test
    @Ignore("Apk dependencies not working in presubmit, b/181810492")
    fun killTest() {
        val scope = MacrobenchmarkScope(PACKAGE_NAME, launchWithClearTask = true)
        scope.pressHome()
        scope.startActivityAndWait()
        assertTrue(isProcessAlive(PACKAGE_NAME))
        scope.killProcess()
        assertFalse(isProcessAlive(PACKAGE_NAME))
    }

    @Test
    @Ignore("Apk dependencies not working in presubmit, b/181810492")
    fun compile_speedProfile() {
        val scope = MacrobenchmarkScope(PACKAGE_NAME, launchWithClearTask = true)
        val iterations = 1
        var executions = 0
        val compilation = CompilationMode.SpeedProfile(warmupIterations = iterations)
        compilation.compile(PACKAGE_NAME) {
            executions += 1
            scope.pressHome()
            scope.startActivityAndWait()
        }
        assertEquals(iterations, executions)
    }

    @Test
    @Ignore("Apk dependencies not working in presubmit, b/181810492")
    fun compile_speed() {
        val compilation = CompilationMode.Speed
        compilation.compile(PACKAGE_NAME) {
            fail("Should never be called for $compilation")
        }
    }

    @Test
    @Ignore("Apk dependencies not working in presubmit, b/181810492")
    fun startActivityAndWait_activityNotExported() {
        val scope = MacrobenchmarkScope(PACKAGE_NAME, launchWithClearTask = true)
        scope.pressHome()

        val intent = Intent()
        intent.setPackage(PACKAGE_NAME)
        intent.action = "$PACKAGE_NAME.NOT_EXPORTED_ACTIVITY"

        // should throw, warning to set exported = true
        val exceptionMessage = assertFailsWith<SecurityException> {
            scope.startActivityAndWait(intent)
        }.message
        assertNotNull(exceptionMessage)
        assertTrue(exceptionMessage.contains("android:exported=true"))
    }

    private fun processes(): List<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = instrumentation.device().executeShellCommand("ps -A")
        return output.split("\r?\n".toRegex())
    }

    private fun isProcessAlive(packageName: String): Boolean {
        return processes().any { it.contains(packageName) }
    }

    companion object {
        private const val PACKAGE_NAME = "androidx.benchmark.integration.macrobenchmark.target"
    }
}
