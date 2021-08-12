@file:OptIn(GlanceInternalApi::class)
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

package androidx.glance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApplierTest {

    @Test
    fun onClear() {
        val root = RootEmittable()
        val applier = Applier(root)
        updateApplier(applier)
        applier.clear()

        assertThat(applier.current).isEqualTo(root)
        assertThat(root.children).isEmpty()
    }

    @Test
    fun insertTopDown() {
        val root = RootEmittable()
        val applier = Applier(root)

        updateApplier(applier)

        assertThat(root.children).hasSize(3)
        assertThat(root.children[0]).isInstanceOf(LeafEmittable::class.java)
        assertThat(root.children[2]).isInstanceOf(LeafEmittable::class.java)
        assertThat(root.children[1]).isInstanceOf(MiddleEmittable::class.java)
        assertThat((root.children[1] as MiddleEmittable).children).hasSize(1)
    }

    @Test
    fun insertBottomUp() {
        val root = RootEmittable()
        val applier = Applier(root)

        applier.insertBottomUp(0, LeafEmittable())

        assertThat(root.children).isEmpty()
    }

    @Test
    fun move() {
        val root = RootEmittable()
        val applier = Applier(root)
        updateApplier(applier)
        applier.up()

        applier.move(0, 2, 1)

        assertThat(root.children).hasSize(3)
        assertThat(root.children[1]).isInstanceOf(LeafEmittable::class.java)
        assertThat(root.children[2]).isInstanceOf(LeafEmittable::class.java)
        assertThat(root.children[0]).isInstanceOf(MiddleEmittable::class.java)
        assertThat((root.children[0] as MiddleEmittable).children).hasSize(1)
    }

    @Test
    fun remove() {
        val root = RootEmittable()
        val applier = Applier(root)
        updateApplier(applier)
        applier.up()

        applier.remove(1, 2)

        assertThat(root.children).hasSize(1)
        assertThat(root.children[0]).isInstanceOf(LeafEmittable::class.java)
    }

    private companion object {
        fun updateApplier(applier: Applier) {
            val middle = MiddleEmittable()
            applier.insertTopDown(0, LeafEmittable())
            applier.insertTopDown(1, middle)
            applier.insertTopDown(2, LeafEmittable())
            applier.down(middle)
            applier.insertTopDown(0, LeafEmittable())
        }
    }
}

private class RootEmittable : EmittableWithChildren() {
    override var modifier: Modifier = Modifier
}

private class MiddleEmittable : EmittableWithChildren() {
    override var modifier: Modifier = Modifier
}

private class LeafEmittable : Emittable {
    override var modifier: Modifier = Modifier
}
