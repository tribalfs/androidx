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

package androidx.tracing.perfetto.binary.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.BaseDexClassLoader
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TracingTest {
    /**
     * The test verifies that the library was assembled and can be found by the system. We cannot
     * load the library since it contains explicit JNI method registration tied to the
     * [androidx.tracing.perfetto.jni.PerfettoNative] class.
     *
     * Methods of the library are further tested in e.g.:
     * - [androidx.tracing.perfetto.jni.test.PerfettoNativeTest]
     * - [androidx.compose.integration.macrobenchmark.TrivialTracingBenchmark]
     */
    @Test
    fun test_library_was_created() {
        // check that the system can resolve the library
        val nativeLibraryName = System.mapLibraryName("tracing_perfetto")

        // check that the class loader can find the library
        val classLoader = javaClass.classLoader as BaseDexClassLoader
        val libraryPath = classLoader.findLibrary("tracing_perfetto")
        assertTrue(libraryPath.endsWith("/$nativeLibraryName"))

        // check that the APK contains the library file
        val context = InstrumentationRegistry.getInstrumentation().context
        val baseApk = File(context.applicationInfo.publicSourceDir!!)
        assertTrue(
            ZipFile(baseApk).entries().asSequence().any { it.name.endsWith("/$nativeLibraryName") }
        )
    }
}
