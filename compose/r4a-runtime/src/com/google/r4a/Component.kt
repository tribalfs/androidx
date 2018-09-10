package com.google.r4a

import com.google.r4a.frames.Framed
import com.google.r4a.frames.Record

@Composable
abstract class Component : Framed, Recomposable {
    internal var recomposeCallback: (() -> Unit)? = null

    protected fun recompose() {
        CompositionContext.recompose(this)
    }

    protected fun recomposeSync() {
        CompositionContext.recomposeSync(this)
    }

    protected lateinit var next: Record
    override val first: Record get() = next
    override fun prepend(value: Record) {
        value.next = next
        next = value
    }

    override fun setRecompose(recompose: () -> Unit) {
        this.recomposeCallback = recompose
    }
}
