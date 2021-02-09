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

package androidx.compose.ui.test.junit4

import android.annotation.SuppressLint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.InfiniteAnimationPolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.textInputServiceFactory
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.compose.ui.test.TestOwner
import androidx.compose.ui.test.createTestContext
import androidx.compose.ui.test.junit4.android.ComposeIdlingResource
import androidx.compose.ui.test.junit4.android.ComposeRootRegistry
import androidx.compose.ui.test.junit4.android.EspressoLink
import androidx.compose.ui.test.junit4.android.awaitComposeRoots
import androidx.compose.ui.test.junit4.android.runEspressoOnIdle
import androidx.compose.ui.test.junit4.android.waitForComposeRoots
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

actual fun createComposeRule(): ComposeContentTestRule =
    createAndroidComposeRule<ComponentActivity>()

/**
 * Factory method to provide android specific implementation of [createComposeRule], for a given
 * activity class type [A].
 *
 * This method is useful for tests that require a custom Activity. This is usually the case for
 * app tests. Make sure that you add the provided activity into your app's manifest file (usually
 * in main/AndroidManifest.xml).
 *
 * This creates a test rule that is using [ActivityScenarioRule] as the activity launcher. If you
 * would like to use a different one you can create [AndroidComposeTestRule] directly and supply
 * it with your own launcher.
 *
 * If you don't care about specific activity and just want to test composables in general, see
 * [createComposeRule].
 */
inline fun <reified A : ComponentActivity> createAndroidComposeRule():
    AndroidComposeTestRule<ActivityScenarioRule<A>, A> {
        // TODO(b/138993381): By launching custom activities we are losing control over what content is
        //  already there. This is issue in case the user already set some compose content and decides
        //  to set it again via our API. In such case we won't be able to dispose the old composition.
        //  Other option would be to provide a smaller interface that does not expose these methods.
        return createAndroidComposeRule(A::class.java)
    }

/**
 * Factory method to provide android specific implementation of [createComposeRule], for a given
 * [activityClass].
 *
 * This method is useful for tests that require a custom Activity. This is usually the case for
 * app tests. Make sure that you add the provided activity into your app's manifest file (usually
 * in main/AndroidManifest.xml).
 *
 * This creates a test rule that is using [ActivityScenarioRule] as the activity launcher. If you
 * would like to use a different one you can create [AndroidComposeTestRule] directly and supply
 * it with your own launcher.
 *
 * If you don't care about specific activity and just want to test composables in general, see
 * [createComposeRule].
 */
fun <A : ComponentActivity> createAndroidComposeRule(
    activityClass: Class<A>
): AndroidComposeTestRule<ActivityScenarioRule<A>, A> = AndroidComposeTestRule(
    activityRule = ActivityScenarioRule(activityClass),
    activityProvider = { it.getActivity() }
)

/**
 * Factory method to provide an implementation of [ComposeTestRule] that doesn't create a host
 * for you in which you can set content. Use this if you don't want the test rule to launch an
 * activity for you, which is typically the case when you launch your activity during the test
 * instead of before the test.
 */
fun createEmptyComposeRule(): ComposeTestRule =
    AndroidComposeTestRule<TestRule, ComponentActivity>(
        activityRule = TestRule { base, _ -> base },
        activityProvider = {
            error(
                "createEmptyComposeRule() does not provide an Activity to set Compose content in." +
                    " Launch and use the Activity yourself, or use createAndroidComposeRule()."
            )
        }
    )

/**
 * Android specific implementation of [ComposeContentTestRule].
 *
 * This rule wraps around the given [activityRule], which is responsible for launching the activity.
 * The [activityProvider] should return the launched activity instance when the [activityRule] is
 * passed to it. In this way, you can provide any test rule that can launch an activity
 *
 * @param activityRule Test rule to use to launch the activity.
 * @param activityProvider To resolve the activity from the given test rule. Must be a blocking
 * function.
 */
@OptIn(InternalTestApi::class)
class AndroidComposeTestRule<R : TestRule, A : ComponentActivity>(
    val activityRule: R,
    private val activityProvider: (R) -> A,
) : ComposeContentTestRule {

    /**
     * Provides the current activity.
     *
     * Avoid calling often as it can involve synchronization and can be slow.
     */
    val activity: A get() = activityProvider(activityRule)

    private val idlingResourceRegistry = IdlingResourceRegistry()
    private val espressoLink = EspressoLink(idlingResourceRegistry)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val composeRootRegistry = ComposeRootRegistry()

    private val mainClockImpl: MainTestClockImpl
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val composeIdlingResource: IdlingResource

    private val recomposer: Recomposer
    @OptIn(ExperimentalCoroutinesApi::class)
    private val testCoroutineDispatcher: TestCoroutineDispatcher
    private val recomposerApplyCoroutineScope: CoroutineScope
    private val frameCoroutineScope: CoroutineScope

    override val mainClock: MainTestClock
        get() = mainClockImpl

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        testCoroutineDispatcher = TestCoroutineDispatcher()
        frameCoroutineScope = CoroutineScope(testCoroutineDispatcher)
        @OptIn(ExperimentalCoroutinesApi::class)
        val frameClock = TestMonotonicFrameClock(frameCoroutineScope)
        mainClockImpl = MainTestClockImpl(testCoroutineDispatcher, frameClock)
        val infiniteAnimationPolicy = object : InfiniteAnimationPolicy {
            override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
                if (mainClockImpl.autoAdvance) {
                    throw CancellationException()
                }
                return block()
            }
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        recomposerApplyCoroutineScope = CoroutineScope(
            testCoroutineDispatcher + frameClock + infiniteAnimationPolicy + Job()
        )
        recomposer = Recomposer(recomposerApplyCoroutineScope.coroutineContext)
            .also { recomposerApplyCoroutineScope.launch { it.runRecomposeAndApplyChanges() } }
        composeIdlingResource = ComposeIdlingResource(
            composeRootRegistry, mainClockImpl, recomposer
        )
        registerIdlingResource(composeIdlingResource)
    }

    internal var disposeContentHook: (() -> Unit)? = null

    private val testOwner = AndroidTestOwner()
    private val testContext = createTestContext(testOwner)

    override val density: Density by lazy {
        Density(ApplicationProvider.getApplicationContext())
    }

    override fun apply(base: Statement, description: Description): Statement {
        @Suppress("NAME_SHADOWING")
        return RuleChain
            .outerRule { base, _ -> composeRootRegistry.getStatementFor(base) }
            .around { base, _ -> idlingResourceRegistry.getStatementFor(base) }
            .around { base, _ -> espressoLink.getStatementFor(base) }
            .around { base, _ -> AndroidComposeStatement(base) }
            .around(activityRule)
            .apply(base, description)
    }

    /**
     * @throws IllegalStateException if called more than once per test.
     */
    @SuppressWarnings("SyntheticAccessor")
    override fun setContent(composable: @Composable () -> Unit) {
        check(disposeContentHook == null) {
            "Cannot call setContent twice per test!"
        }

        // We always make sure we have the latest activity when setting a content
        val currentActivity = activity

        runOnUiThread {
            currentActivity.setContent(recomposer, composable)
            disposeContentHook = {
                // Removing a default ComposeView from the view hierarchy will
                // dispose its composition.
                activity.setContentView(View(activity))
            }
        }

        if (!isOnUiThread()) {
            // Only wait for idleness if not on the UI thread. If we are on the UI thread, the
            // caller clearly wants to keep tight control over execution order, so don't go
            // executing future tasks on the main thread.
            waitForIdle()
        }
    }

    override fun waitForIdle() {
        check(!isOnUiThread()) {
            "Functions that involve synchronization (Assertions, Actions, Synchronization; " +
                "e.g. assertIsSelected(), doClick(), runOnIdle()) cannot be run " +
                "from the main thread. Did you nest such a function inside " +
                "runOnIdle {}, runOnUiThread {} or setContent {}?"
        }

        // First wait until we have a compose root (in case an Activity is being started)
        composeRootRegistry.waitForComposeRoots()
        // Then await composition(s)
        runEspressoOnIdle()

        // TODO(b/155774664): waitForComposeRoots() may be satisfied by a compose root from an
        //  Activity that is about to be paused, in cases where a new Activity is being started.
        //  That means that ComposeRootRegistry.getComposeRoots() may still return an empty list
        //  between now and when the new Activity has created its compose root, even though
        //  waitForComposeRoots() suggests that we are now guaranteed one.
    }

    override suspend fun awaitIdle() {
        // TODO(b/169038516): when we can query compose roots for measure or layout, remove
        //  runEspressoOnIdle() and replace it with a suspend fun that loops while the
        //  snapshot or the recomposer has pending changes, clocks are busy or compose roots have
        //  pending measures or layouts; and do the await on AndroidUiDispatcher.Main
        // We use Espresso to wait for composition, measure, layout and draw,
        // and Espresso needs to be called from a non-ui thread; so use Dispatchers.IO
        withContext(Dispatchers.IO) {
            // First wait until we have a compose root (in case an Activity is being started)
            composeRootRegistry.awaitComposeRoots()
            // Then await composition(s)
            runEspressoOnIdle()
        }
    }

    override fun <T> runOnUiThread(action: () -> T): T {
        return testOwner.runOnUiThread(action)
    }

    override fun <T> runOnIdle(action: () -> T): T {
        // Method below make sure that compose is idle.
        waitForIdle()
        // Execute the action on ui thread in a blocking way.
        return runOnUiThread(action)
    }

    @SuppressWarnings("DocumentExceptions") // The interface doc already documents this
    override fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val startTime = System.nanoTime()
        while (!condition()) {
            if (mainClockImpl.autoAdvance) {
                mainClock.advanceTimeByFrame()
            }
            // Let Android run measure, draw and in general any other async operations.
            Thread.sleep(10)
            if (System.nanoTime() - startTime > timeoutMillis * 1_000_000) {
                throw ComposeTimeoutException(
                    "Condition still not satisfied after $timeoutMillis ms"
                )
            }
        }
    }

    override fun registerIdlingResource(idlingResource: IdlingResource) {
        idlingResourceRegistry.registerIdlingResource(idlingResource)
    }

    override fun unregisterIdlingResource(idlingResource: IdlingResource) {
        idlingResourceRegistry.unregisterIdlingResource(idlingResource)
    }

    inner class AndroidComposeStatement(
        private val base: Statement
    ) : Statement() {

        @OptIn(InternalComposeUiApi::class)
        override fun evaluate() {
            WindowRecomposerPolicy.withFactory({ recomposer }) {
                evaluateInner()
            }
        }

        @OptIn(InternalComposeUiApi::class)
        private fun evaluateInner() {
            val oldTextInputFactory = textInputServiceFactory
            try {
                textInputServiceFactory = {
                    TextInputServiceForTests(it)
                }
                base.evaluate()
            } finally {
                recomposer.cancel()
                // FYI: Not canceling these scope below would end up cleanupTestCoroutines
                // throwing errors on active coroutines
                recomposerApplyCoroutineScope.cancel()
                frameCoroutineScope.cancel()
                @OptIn(ExperimentalCoroutinesApi::class)
                testCoroutineDispatcher.cleanupTestCoroutines()
                textInputServiceFactory = oldTextInputFactory
                // Dispose the content
                if (disposeContentHook != null) {
                    runOnUiThread {
                        // NOTE: currently, calling dispose after an exception that happened during
                        // composition is not a safe call. Compose runtime should fix this, and then
                        // this call will be okay. At the moment, however, calling this could
                        // itself produce an exception which will then obscure the original
                        // exception. To fix this, we will just wrap this call in a try/catch of
                        // its own
                        try {
                            disposeContentHook!!()
                        } catch (e: Exception) {
                            // ignore
                        }
                        disposeContentHook = null
                    }
                }
            }
        }
    }

    override fun onNode(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean
    ): SemanticsNodeInteraction {
        return SemanticsNodeInteraction(testContext, useUnmergedTree, matcher)
    }

    override fun onAllNodes(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean
    ): SemanticsNodeInteractionCollection {
        return SemanticsNodeInteractionCollection(testContext, useUnmergedTree, matcher)
    }

    @OptIn(InternalTestApi::class)
    internal inner class AndroidTestOwner : TestOwner {

        override val mainClock: MainTestClock
            get() = mainClockImpl

        @SuppressLint("DocumentExceptions")
        override fun sendTextInputCommand(node: SemanticsNode, command: List<EditCommand>) {
            val owner = node.root as ViewRootForTest

            runOnIdle {
                val textInputService = owner.getTextInputServiceOrDie()
                val onEditCommand = textInputService.onEditCommand
                    ?: throw IllegalStateException("No input session started. Missing a focus?")
                onEditCommand(command)
            }
        }

        @SuppressLint("DocumentExceptions")
        override fun sendImeAction(node: SemanticsNode, actionSpecified: ImeAction) {
            val owner = node.root as ViewRootForTest

            runOnIdle {
                val textInputService = owner.getTextInputServiceOrDie()
                val onImeActionPerformed = textInputService.onImeActionPerformed
                    ?: throw IllegalStateException("No input session started. Missing a focus?")
                onImeActionPerformed.invoke(actionSpecified)
            }
        }

        @SuppressLint("DocumentExceptions")
        override fun <T> runOnUiThread(action: () -> T): T {
            return androidx.compose.ui.test.junit4.runOnUiThread(action)
        }

        override fun getRoots(): Set<RootForTest> {
            // TODO(pavlis): Instead of returning a flatMap, let all consumers handle a tree
            //  structure. In case of multiple AndroidOwners, add a fake root
            waitForIdle()

            return composeRootRegistry.getRegisteredComposeRoots().also {
                // TODO(b/153632210): This check should be done by callers of getOwners()
                check(it.isNotEmpty()) {
                    "No compose views found in the app. Is your Activity resumed?"
                }
            }
        }

        private fun ViewRootForTest.getTextInputServiceOrDie(): TextInputServiceForTests {
            return textInputService as? TextInputServiceForTests
                ?: throw IllegalStateException(
                    "Text input service wrapper not set up! Did you use ComposeTestRule?"
                )
        }
    }
}

private fun <A : ComponentActivity> ActivityScenarioRule<A>.getActivity(): A {
    var activity: A? = null
    scenario.onActivity { activity = it }
    if (activity == null) {
        throw IllegalStateException("Activity was not set in the ActivityScenarioRule!")
    }
    return activity!!
}
