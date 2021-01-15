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

package androidx.compose.runtime

/**
 * Represents a recomposable scope or section of the composition hierarchy. Can be used to
 * manually invalidate the scope to schedule it for recomposition.
 */
interface RecomposeScope {
    /**
     * Invalidate the corresponding scope, requesting the composer recompose this scope.
     */
    fun invalidate()
}

/**
 * Returns an object which can be used to invalidate the current scope at this point in composition.
 * This object can be used to manually cause recompositions.
 */
val currentRecomposeScope: RecomposeScope
    @ComposableContract(readonly = true)
    @Composable get() {
        val scope = currentComposer.currentRecomposeScope ?: error("no recompose scope found")
        scope.used = true
        return scope
    }

/**
 * A RecomposeScope is created for a region of the composition that can be recomposed independently
 * of the rest of the composition. The composer will position the slot table to the location
 * stored in [anchor] and call [block] when recomposition is requested. It is created by
 * [Composer.startRestartGroup] and is used to track how to restart the group.
 */
@OptIn(ComposeCompilerApi::class)
internal class RecomposeScopeImpl(var composer: Composer<*>?) : ScopeUpdateScope, RecomposeScope {
    /**
     * An anchor to the location in the slot table that start the group associated with this
     * recompose scope.
     */
    var anchor: Anchor? = null

    /**
     * Return whether the scope is valid. A scope becomes invalid when the slots it updates are
     * removed from the slot table. For example, if the scope is in the then clause of an if
     * statement that later becomes false.
     */
    val valid: Boolean get() = composer != null && anchor?.valid ?: false

    /**
     * Used is set when the [RecomposeScopeImpl] is used by, for example, [currentRecomposeScope].
     * This is used as the result of [Composer.endRestartGroup] and indicates whether the lambda
     * that is stored in [block] will be used.
     */
    var used = false

    /**
     * Set to true when the there are function default calculations in the scope. These are
     * treated as a special case to avoid having to create a special scope for them. If these
     * change the this scope needs to be recomposed but the default values can be skipped if they
     * where not invalidated.
     */
    var defaultsInScope = false

    /**
     * Tracks whether any of the calculations in the default values were changed. See
     * [defaultsInScope] for details.
     */
    var defaultsInvalid = false

    /**
     * Tracks whether the scope was invalidated directly but was recomposed because the caller
     * was recomposed. This ensures that a scope invalidated directly will recompose even if its
     * parameters are the same as the previous recomposition.
     */
    var requiresRecompose = false

    /**
     * The lambda to call to restart the scopes composition.
     */
    private var block: ((Composer<*>, Int) -> Unit)? = null

    /**
     * Restart the scope's composition. It is an error if [block] was not updated. The code
     * generated by the compiler ensures that when the recompose scope is used then [block] will
     * be set but it might occur if the compiler is out-of-date (or ahead of the runtime) or
     * incorrect direct calls to [Composer.startRestartGroup] and [Composer.endRestartGroup].
     */
    fun <N> compose(composer: Composer<N>) {
        block?.invoke(composer, 1) ?: error("Invalid restart scope")
    }

    /**
     * Invalidate the group which will cause [composer] to request this scope be recomposed,
     * and an [InvalidationResult] will be returned.
     */
    fun invalidateForResult(): InvalidationResult =
        composer?.invalidate(this) ?: InvalidationResult.IGNORED

    /**
     * Invalidate the group which will cause [composer] to request this scope be recomposed.
     */
    override fun invalidate() {
        invalidateForResult()
    }

    /**
     * Update [block]. The scope is returned by [Composer.endRestartGroup] when [used] is true
     * and implements [ScopeUpdateScope].
     */
    override fun updateScope(block: (Composer<*>, Int) -> Unit) { this.block = block }
}