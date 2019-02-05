package com.google.r4a

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

object R4a {

    private class Root: Component() {
        lateinit var composable: @Composable() () -> Unit
        override fun compose() {
            val cc = composer.composer
            cc.startGroup(0)
            composable()
            cc.endGroup()
        }
    }


    fun composeInto(
        container: ViewGroup,
        parent: Ambient.Reference? = null,
        composable: () -> Unit
    ) {
        var root = CompositionContext.getRootComponent(container) as? Root
        if (root == null) {
            container.removeAllViews()
            root = Root()
            root.composable = composable
            val cc = CompositionContext.create(container.context, container, root, parent)
            cc.recomposeSync(root)
        } else {
            root.composable = composable
            CompositionContext.recomposeSync(root)
        }
    }

    fun composeInto(
        container: Emittable,
        context: Context,
        parent: Ambient.Reference? = null,
        composable: () -> Unit
    ) {
        var root = CompositionContext.getRootComponent(container) as? Root
        if (root == null) {
            root = Root()
            root.composable = composable
            CompositionContext.setRoot(container, root)
            val cc = CompositionContext.create(context, container, root, parent)
            cc.recomposeSync(root)
        } else {
            root.composable = composable
            CompositionContext.recomposeSync(root)
        }
    }
}

fun Activity.composeInto(composable: @Composable() () -> Unit) = setContentView(composable)
fun Activity.setContent(composable: @Composable() () -> Unit) = setContentView(composable)
fun Activity.setContentView(composable: @Composable() () -> Unit) = setContentView(FrameLayout(this).apply { composeInto(composable) })
fun ViewGroup.composeInto(composable: @Composable() () -> Unit) = R4a.composeInto(this, null, composable)
