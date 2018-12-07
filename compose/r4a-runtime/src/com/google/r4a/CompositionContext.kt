package com.google.r4a

import android.content.Context
import android.view.View
import java.util.*

abstract class CompositionContext {
    companion object {

        private val TAG_ROOT_COMPONENT = "r4aRootComponent".hashCode()
        private val EMITTABLE_ROOT_COMPONENT = WeakHashMap<Emittable, Component>()
        private val COMPONENTS_TO_CONTEXT = WeakHashMap<Component, CompositionContext>()

        val factory: Function4<Context, Any, Component, Ambient.Reference?, CompositionContext> get() =
            ComposerCompositionContext.factory

        var current: CompositionContext = EmptyCompositionContext()

        fun create(context: Context, group: Any, component: Component, reference: Ambient.Reference?): CompositionContext {
            val cc = factory(context, group, component, reference)
            when (group) {
                is View -> setRoot(group, component)
                is Emittable -> setRoot(group, component)
            }
            return cc
        }

        fun find(component: Component): CompositionContext? {
            return COMPONENTS_TO_CONTEXT[component]
        }

        fun associate(component: Component, context: CompositionContext) {
            COMPONENTS_TO_CONTEXT[component] = context
        }

        fun recompose(component: Component) {
            find(component)?.recompose(component)
        }

        fun recomposeSync(component: Component) {
            find(component)?.recomposeSync(component)
        }

        fun find(view: View): Component? {
            var node: View? = view
            while (node != null) {
                val cc = node.getTag(TAG_ROOT_COMPONENT) as? Component
                if (cc != null) return cc
                node = node.parent as? View
            }
            return null
        }

        fun getRootComponent(view: View): Component? {
            return view.getTag(TAG_ROOT_COMPONENT) as? Component
        }

        fun getRootComponent(emittable: Emittable): Component? {
            return EMITTABLE_ROOT_COMPONENT[emittable]
        }

        fun setRoot(view: View, component: Component) {
            view.setTag(TAG_ROOT_COMPONENT, component)
        }

        fun setRoot(emittable: Emittable, component: Component) {
            EMITTABLE_ROOT_COMPONENT[emittable] = component
        }

        fun <T : Any?> getAmbient(key: Ambient<T>, component: Component): T = find(component)!!.getAmbient(key, component)
    }

    abstract fun startRoot()
    abstract fun start(sourceHash: Int)
    abstract fun start(sourceHash: Int, key: Any?)
    abstract fun startView(sourceHash: Int)
    abstract fun startView(sourceHash: Int, key: Any?)
    abstract fun setInstance(instance: Any)
    abstract fun useInstance(): Any?
    abstract fun isInserting(): Boolean
    abstract fun startCompose(willCompose: Boolean)
    abstract fun endCompose(didCompose: Boolean)
    abstract fun startCall(willCall: Boolean)
    abstract fun endCall(didCall: Boolean)
    abstract fun attributeChanged(value: Any?): Boolean
    abstract fun attributeChangedOrInserting(value: Any?): Boolean
    abstract fun end()
    abstract fun endView()
    abstract fun endRoot()
    abstract fun applyChanges()
    abstract fun joinKey(left: Any?, right: Any?): Any

    abstract var context: Context
    abstract fun recompose(component: Component)
    abstract fun recomposeAll()
    abstract fun recomposeSync(component: Component)
    abstract fun preserveAmbientScope(component: Component)
    abstract fun <T : Any?> getAmbient(key: Ambient<T>): T
    abstract fun <T : Any?> getAmbient(key: Ambient<T>, component: Component): T
    abstract fun debug()
}


class Updater<T>(
    val cc: CompositionContext,
    val el: T
) {
    inline fun <reified V> set(value: V, noinline block: T.(V) -> Unit) {
        if (cc.attributeChangedOrInserting(value)) {
            el.block(value)
        }
    }
}

inline fun CompositionContext.group(key: Int, key2: Any? = null, block: () -> Unit = {}) {
    start(key, key2)
    block()
    end()
}

inline fun CompositionContext.viewGroup(key: Int, key2: Any? = null, block: () -> Unit = {}) {
    startView(key, key2)
    block()
    endView()
}

inline fun <reified T : Component> CompositionContext.emitComponent(
    loc: Int,
    ctor: () -> T,
    block: Updater<T>.() -> Unit
) = emitComponent(loc, null, ctor, block)

inline fun <reified T : Component> CompositionContext.emitComponent(
    loc: Int,
    ctor: () -> T
) = emitComponent(loc, null, ctor, {})

inline fun <reified T : Component> CompositionContext.emitComponent(
    loc: Int,
    key: Int?,
    ctor: () -> T
) = emitComponent(loc, key, ctor, {})

inline fun <reified T : Component> CompositionContext.emitComponent(
    loc: Int,
    key: Int?,
    ctor: () -> T,
    block: Updater<T>.() -> Unit
) = group(loc, key) {
    val el: T
    if (isInserting()) {
        el = ctor()
        setInstance(el)
    } else {
        el = useInstance() as T
    }
    Updater(this, el).block()
    // TODO(lmr): do pruning
    startCompose(true)
    el.compose()
    endCompose(true)
}


inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    ctor: (context: Context) -> T,
    updater: Updater<T>.() -> Unit,
    block: () -> Unit
) = emitView(loc, null, ctor, updater, block)

inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    ctor: (context: Context) -> T,
    updater: Updater<T>.() -> Unit
) = emitView(loc, null, ctor, updater, {})

inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    ctor: (context: Context) -> T
) = emitView(loc, null, ctor, {}, {})

inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    key: Int?,
    ctor: (context: Context) -> T,
    updater: Updater<T>.() -> Unit
) = emitView(loc, key, ctor, updater, {})

inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    key: Int?,
    ctor: (context: Context) -> T
) = emitView(loc, key, ctor, {}, {})

inline fun <reified T : View> CompositionContext.emitView(
    loc: Int,
    key: Int?,
    ctor: (context: Context) -> T,
    updater: Updater<T>.() -> Unit,
    block: () -> Unit
) = viewGroup(loc, key) {
    val el: T
    if (isInserting()) {
        el = ctor(context)
        setInstance(el)
    } else {
        el = useInstance() as T
    }
    Updater(this, el).updater()
    block()
}

inline fun <reified T: Emittable> CompositionContext.emitEmittable(
    loc: Int,
    key: Int?,
    ctor: () -> T,
    updater: Updater<T>.() -> Unit,
    block: () -> Unit
) = viewGroup(loc, key) {
    val el: T = if (isInserting()) ctor().also { setInstance(it) } else  useInstance() as T
    Updater(this, el).updater()
    block()
}

inline fun <reified T: Emittable> CompositionContext.emitEmittable(
    loc: Int,
    ctor: () -> T,
    updater: Updater<T>.() -> Unit,
    block: () -> Unit
) = emitEmittable(loc, null, ctor, updater, block)

inline fun <reified T: Emittable> CompositionContext.emitEmittable(
    loc: Int, ctor: () -> T, updater: Updater<T>.() -> Unit
) = emitEmittable(loc, null, ctor, updater, {})

inline fun <reified T> CompositionContext.provideAmbient(
    key: Ambient<T>,
    value: T,
    noinline children: () -> Unit
) = group(0) {
    val el: Ambient<T>.Provider
    if (isInserting()) {
        el = key.Provider(value, children)
        setInstance(el)
    } else {
        @Suppress("UNCHECKED_CAST")
        el = useInstance() as Ambient<T>.Provider
    }
    if (attributeChanged(value)) {
        el.value = value
    }
    if (attributeChanged(children)) {
        el.children = children
    }
    startCompose(true)
    el.compose()
    endCompose(true)
}

inline fun <reified T> CompositionContext.consumeAmbient(
    key: Ambient<T>,
    noinline children: (T) -> Unit
) = group(0) {
    val el: Ambient<T>.Consumer
    if (isInserting()) {
        el = key.Consumer(children)
        setInstance(el)
    } else {
        @Suppress("UNCHECKED_CAST")
        el = useInstance() as Ambient<T>.Consumer
    }
    if (attributeChangedOrInserting(children)) {
        el.children = children
    }
    startCompose(true)
    el.compose()
    endCompose(true)
}

@Suppress("NOTHING_TO_INLINE")
inline fun CompositionContext.portal(location: Int, noinline children: (Ambient.Reference) -> Unit) {
    emitComponent(location, { Ambient.Portal(children) }) {
        set(children) { this.children = it }
    }
}
