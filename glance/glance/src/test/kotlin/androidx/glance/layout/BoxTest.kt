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

package androidx.glance.layout

import androidx.glance.GlanceInternalApi
import androidx.glance.Modifier
import androidx.glance.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test

@OptIn(GlanceInternalApi::class, ExperimentalCoroutinesApi::class)
class BoxTest {
    private lateinit var fakeCoroutineScope: TestCoroutineScope

    @Before
    fun setUp() {
        fakeCoroutineScope = TestCoroutineScope()
    }

    @Test
    fun createComposableBox() = fakeCoroutineScope.runBlockingTest {
        val root = runTestingComposition {
            Box {}
        }

        // Outer box (added by runTestingComposition) should have a single child box.
        assertThat(root.children).hasSize(1)
        assertThat(root.children[0]).isInstanceOf(EmittableBox::class.java)

        // The Box added above should not have any other children.
        assertThat((root.children[0] as EmittableBox).children).hasSize(0)
    }

    @Test
    fun createComposableBoxWithModifier() = fakeCoroutineScope.runBlockingTest {
        val root = runTestingComposition {
            Box(modifier = Modifier.padding(1.dp)) {}
        }

        val innerBox = root.children[0] as EmittableBox
        val paddingModifier = requireNotNull(innerBox.modifier.findModifier<PaddingModifier>())

        // Don't need to test all elements, that's covered in PaddingTest
        assertThat(paddingModifier.top).isEqualTo(1.dp)
    }

    @Test
    fun createComposableBoxWithAlignment() = fakeCoroutineScope.runBlockingTest {
        val root = runTestingComposition {
            Box(contentAlignment = Alignment.Center) {}
        }

        val innerBox = root.children[0] as EmittableBox

        assertThat(innerBox.contentAlignment).isEqualTo(Alignment.Center)
    }

    @Test
    fun createComposableBoxWithChildren() = fakeCoroutineScope.runBlockingTest {
        val root = runTestingComposition {
            Box(contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomCenter) {}
                Box(contentAlignment = Alignment.TopCenter) {}
            }
        }

        val innerBox = root.children[0] as EmittableBox

        assertThat(innerBox.children).hasSize(2)

        val leafBox0 = innerBox.children[0] as EmittableBox
        val leafBox1 = innerBox.children[1] as EmittableBox

        assertThat(leafBox0.contentAlignment).isEqualTo(Alignment.BottomCenter)
        assertThat(leafBox1.contentAlignment).isEqualTo(Alignment.TopCenter)
    }
}