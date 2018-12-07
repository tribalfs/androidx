/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package com.google.r4a

import android.content.Context
import android.view.Choreographer
import android.view.View
import com.google.r4a.adapters.Dimension
import java.util.WeakHashMap

internal class ComposerCompositionContext(val root: Any, private val rootComponent: Component) : CompositionContext() {
    companion object {
        val factory: Function4<Context, Any, Component, Ambient.Reference?, CompositionContext> by lazy {
            object : Function4<Context, Any, Component, Ambient.Reference?, CompositionContext> {
                override fun invoke(
                    context: Context,
                    root: Any,
                    component: Component,
                    ambientReference: Ambient.Reference?
                ): CompositionContext {
                    val result = ComposerCompositionContext(root, component)
                    result.context = context
                    result.ambientReference = ambientReference
                    FrameManager.registerComposition(result)
                    return result
                }
            }
        }
    }

    internal val composer by lazy { ViewComposer(root, context) }
    private var currentComponent: Component? = null
    private var ambientReference: Ambient.Reference? = null

    private var hasPendingFrame = false
    private var isComposing = false

    private val preservedAmbientScopes by lazy { WeakHashMap<Component, List<Ambient<*>.Provider>>() }

    private val frameCallback = Choreographer.FrameCallback {
        hasPendingFrame = false
        recomposePending()
    }

    private fun recomposePending() {
        if (isComposing) return
        val prev = CompositionContext.current
        try {
            isComposing = true
            CompositionContext.current = this
            composer.recompose()
            composer.applyChanges()
        } finally {
            CompositionContext.current = prev
            isComposing = false
        }
    }

    override lateinit var context: Context

    override fun startRoot() {
        composer.slots.reset()
        composer.slots.beginReading()
        composer.startGroup(0)
    }

    override fun start(sourceHash: Int) = composer.startGroup(sourceHash)
    override fun start(sourceHash: Int, key: Any?) = composer.startGroup(if (key != null) composer.joinKey(sourceHash, key) else sourceHash)
    override fun startView(sourceHash: Int) = composer.startNode(sourceHash)
    override fun startView(sourceHash: Int, key: Any?) =
        composer.startNode(if (key != null) composer.joinKey(sourceHash, key) else sourceHash)

    override fun end() = composer.endGroup()
    override fun endView() = composer.endNode()

    override fun endRoot() {
        composer.endGroup()
        composer.slots.endReading()
        composer.finalizeCompose()
    }

    override fun joinKey(left: Any?, right: Any?): Any = composer.joinKey(left, right)

    override fun applyChanges() = composer.applyChanges()

    override fun setInstance(instance: Any) {
        assert(currentComponent == null) { "Unhandled recursion" }
        when (instance) {
            is View -> composer.emitNode(instance)
            is Emittable -> composer.emitNode(instance)
            is Component -> {
                currentComponent = instance
                composer.updateValue(instance)
                CompositionContext.associate(instance, this)
            }
            else -> error("Unknown instance type ${instance.javaClass}")
        }
    }

    override fun useInstance(): Any? {
        val instance = composer.peekSlot()
        return when (instance) {
            is View, is Emittable -> composer.useNode()
            is Component -> {
                composer.skipValue()
                composer.nextSlot()
                currentComponent = instance
                instance
            }
            else -> error("Unknown instance type $instance")
        }
    }

    override fun isInserting(): Boolean = composer.inserting

    override fun startCompose(willCompose: Boolean) {
        val component = currentComponent
        if (component != null) {
            composer.startCompose(!willCompose, component)
        }
        currentComponent = null
    }

    override fun endCompose(didCompose: Boolean) = composer.doneCompose(!didCompose)

    override fun startCall(willCall: Boolean) {
        if (willCall) {
            composer.startGroup(0)
        } else {
            composer.skipGroup(0)
        }
    }

    override fun endCall(didCall: Boolean) {
        if (didCall) composer.endGroup()
    }

    override fun attributeChanged(value: Any?) =
        if (composer.nextSlot() == value && isEffectivelyImmutable(value)) {
            composer.skipValue()
            false
        } else {
            composer.updateValue(value)
            true
        }

    /**
     * Just a dummy implementation to prove the behavior for a couple simple cases.
     * TODO: Should return true for deeply immutable objects, frozen objects, primitives, value types, inline classes of immutables, @Model
     * TODO: When we know at compile time, we shouldn't be doing a runtime check for this
     */
    private fun isEffectivelyImmutable(value: Any?): Boolean {
        return when(value) {
            is String, is Int, is Double, is Float, is Short, is Byte, is Char, is Boolean, is UByte, is UShort, is UInt, is ULong -> true
            is Dimension -> true
            else -> false
        }
    }

    override fun attributeChangedOrInserting(value: Any?): Boolean = attributeChanged(value) || composer.inserting

    override fun recomposeAll() {
        // if we're not currently composing and a frame hasn't been scheduled, we want to schedule it
        if (!isComposing && !hasPendingFrame) {
            hasPendingFrame = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun recompose(component: Component) {
        component.recomposeCallback?.let { it() }
        recomposeAll()
    }

    override fun recomposeSync(component: Component) {
        if (component == rootComponent) {
            val previousComposing = isComposing
            val prev = CompositionContext.current
            try {
                isComposing = true
                CompositionContext.current = this
                startRoot()
                if (isInserting())
                    setInstance(component)
                else
                    useInstance()
                startCompose(true)
                component.compose()
                endCompose(true)
                endRoot()
                applyChanges()
            } finally {
                CompositionContext.current = prev
                isComposing = previousComposing
            }
        } else {
            component.recomposeCallback?.let { it() }
            if (!isComposing) {
                hasPendingFrame = false
                recomposePending()
            }
        }
    }

    override fun preserveAmbientScope(component: Component) {
        val providers = mutableListOf<Ambient<*>.Provider>()
        composer.enumParents { parent ->
            if (parent is Ambient<*>.Provider) providers.add(parent)
            true
        }
        if (providers.size > 0)
            preservedAmbientScopes[component] = providers
    }

    override fun <T> getAmbient(key: Ambient<T>): T {
        var result: Any? = null
        composer.enumParents { parent ->
            if (parent is Ambient<*>.Provider && parent.ambient == key) {
                result = parent.value
                false
            } else true
        }

        if (result == null) {
            val ref = ambientReference
            if (ref != null) {
                return ref.getAmbient(key)
            }
            return key.defaultValue
        }

        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getAmbient(key: Ambient<T>, component: Component): T =
        preservedAmbientScopes[component]?.firstOrNull { it.ambient == key }?.value as T? ?: ambientReference?.getAmbient(key)
        ?: key.defaultValue

    override fun debug() {}
}
