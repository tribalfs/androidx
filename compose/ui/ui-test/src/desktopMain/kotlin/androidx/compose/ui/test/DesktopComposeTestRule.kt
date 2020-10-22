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

package androidx.compose.ui.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.DesktopOwner
import androidx.compose.ui.platform.DesktopOwners
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import org.jetbrains.skija.Surface
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.LinkedList
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import javax.swing.SwingUtilities.invokeAndWait

actual fun createComposeRule(): ComposeTestRuleJUnit = DesktopComposeTestRule()

class DesktopComposeTestRule : ComposeTestRuleJUnit {

    companion object {
        init {
            initCompose()
        }

        var current: DesktopComposeTestRule? = null
    }

    var owners: DesktopOwners? = null
    private var owner: DesktopOwner? = null

    override val clockTestRule: AnimationClockTestRule = DesktopAnimationClockTestRule()

    override val density: Density
        get() = TODO()

    override val displaySize: IntSize get() = IntSize(1024, 768)

    val executionQueue = LinkedList<() -> Unit>()

    override fun apply(base: Statement, description: Description?): Statement {
        current = this
        return object : Statement() {
            override fun evaluate() {
                base.evaluate()
                runExecutionQueue()
                owner?.dispose()
                owner = null
            }
        }
    }

    private fun runExecutionQueue() {
        while (executionQueue.isNotEmpty()) {
            executionQueue.removeFirst()()
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    private fun isIdle() =
        !Snapshot.current.hasPendingChanges() &&
            !Recomposer.current().hasInvalidations()

    override fun waitForIdle() {
        while (!isIdle()) {
            runExecutionQueue()
            Thread.sleep(10)
        }
    }

    @ExperimentalTesting
    override suspend fun awaitIdle() {
        while (!isIdle()) {
            runExecutionQueue()
            delay(10)
        }
    }

    override fun <T> runOnUiThread(action: () -> T): T {
        val task: FutureTask<T> = FutureTask(action)
        invokeAndWait(task)
        try {
            return task.get()
        } catch (e: ExecutionException) { // Expose the original exception
            throw e.cause!!
        }
    }

    override fun <T> runOnIdle(action: () -> T): T {
        // We are waiting for idle before and AFTER `action` to guarantee that changes introduced
        // in `action` are propagated to components. In Android's version, it's executed in the
        // Main thread which has similar effects. This code could be reconsidered after
        // stabilization of the new rendering/dispatching model
        waitForIdle()
        return action().also { waitForIdle() }
    }

    override fun setContent(composable: @Composable () -> Unit) {
        check(owner == null) {
            "Cannot call setContent twice per test!"
        }
        val surface = Surface.makeRasterN32Premul(displaySize.width, displaySize.height)
        val canvas = surface.canvas
        val owners = DesktopOwners(invalidate = {}).also {
            owners = it
        }
        val owner = DesktopOwner(owners)
        owner.setContent(composable)
        owner.setSize(displaySize.width, displaySize.height)
        owner.measureAndLayout()
        owner.draw(canvas)
        this.owner = owner
    }
}