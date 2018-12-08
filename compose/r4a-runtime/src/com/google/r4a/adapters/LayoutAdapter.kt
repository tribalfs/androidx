package com.google.r4a.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

private var registered = false
private val View.layoutBuilder: LayoutBuilder
    get() {
        if (!registered) {
            registerHandlers()
        }
        return getOrAddLayoutBuilderAdapter()
    }

private fun registerHandlers() {
    registerIntLayoutHandler(android.R.attr.layout_width) { width = it }
    registerIntLayoutHandler(android.R.attr.layout_height) { height = it }
    registerFloatLayoutHandler(android.R.attr.layout_weight) {
        when (this) {
            is LinearLayout.LayoutParams -> weight = it
            else -> error("weight not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_gravity) {
        when (this) {
            is LinearLayout.LayoutParams -> gravity = it
            is FrameLayout.LayoutParams -> gravity = it
            else -> error("gravity not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_margin) {
        when (this) {
            is ViewGroup.MarginLayoutParams -> setMargins(it, it, it, it)
            else -> error("margin not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_marginTop) {
        when (this) {
            is ViewGroup.MarginLayoutParams -> topMargin = it
            else -> error("marginTop not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_marginLeft) {
        when (this) {
            is ViewGroup.MarginLayoutParams -> leftMargin = it
            else -> error("marginLeft not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_marginBottom) {
        when (this) {
            is ViewGroup.MarginLayoutParams -> bottomMargin = it
            else -> error("marginBottom not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registerIntLayoutHandler(android.R.attr.layout_marginRight) {
        when (this) {
            is ViewGroup.MarginLayoutParams -> rightMargin = it
            else -> error("marginRight not possible to be set on ${this::class.java.simpleName}")
        }
    }
    registered = true
}

private fun View.setPixelLayoutWidth(width: Int) = layoutBuilder.set(android.R.attr.layout_width, width)
private fun View.setPixelLayoutHeight(height: Int) = layoutBuilder.set(android.R.attr.layout_height, height)
private fun View.setPixelMarginTop(pixels: Int) = layoutBuilder.set(android.R.attr.layout_marginTop, pixels)
private fun View.setPixelMarginLeft(pixels: Int) = layoutBuilder.set(android.R.attr.layout_marginLeft, pixels)
private fun View.setPixelMarginBottom(pixels: Int) = layoutBuilder.set(android.R.attr.layout_marginBottom, pixels)
private fun View.setPixelMarginRight(pixels: Int) = layoutBuilder.set(android.R.attr.layout_marginRight, pixels)
private fun View.setPixelMarginHorizontal(pixels: Int) {
    setPixelMarginLeft(pixels)
    setPixelMarginRight(pixels)
}
private fun View.setPixelMarginVertical(pixels: Int) {
    setPixelMarginTop(pixels)
    setPixelMarginBottom(pixels)
}

fun View.setLayoutWidth(dim: Dimension) = setPixelLayoutWidth(dim.toIntPixels(metrics))
fun View.setLayoutWidth(width: Int) {
    if (width == -1 || width == -2) {
        // It is either MATCH_PARENT, FILL_PARENT or WRAP_CONTENT
        setPixelLayoutWidth(width)
    } else {
        // It is a dimension resource ID.
        setPixelLayoutWidth(resources.getDimensionPixelSize(width.assertDimensionRes()))
    }
}

fun View.setLayoutHeight(dim: Dimension) = setPixelLayoutHeight(dim.toIntPixels(metrics))
fun View.setLayoutHeight(height: Int) {
    if (height == -1 || height == -2) {
        setPixelLayoutHeight(height)
    } else {
        setPixelLayoutHeight(resources.getDimensionPixelSize(height.assertDimensionRes()))
    }
}

fun View.setLayoutGravity(gravity: Int) = layoutBuilder.set(android.R.attr.layout_gravity, gravity)

fun View.setMarginTop(resId: Int) = setPixelMarginTop(resources.getDimensionPixelSize(resId.assertDimensionRes()))
fun View.setMarginLeft(resId: Int) = setPixelMarginLeft(resources.getDimensionPixelSize(resId.assertDimensionRes()))
fun View.setMarginBottom(resId: Int) = setPixelMarginBottom(resources.getDimensionPixelSize(resId.assertDimensionRes()))
fun View.setMarginRight(resId: Int) = setPixelMarginRight(resources.getDimensionPixelSize(resId.assertDimensionRes()))

fun View.setMarginTop(dim: Dimension) = setPixelMarginTop(dim.toIntPixels(metrics))
fun View.setMarginLeft(dim: Dimension) = setPixelMarginLeft(dim.toIntPixels(metrics))
fun View.setMarginBottom(dim: Dimension) = setPixelMarginBottom(dim.toIntPixels(metrics))
fun View.setMarginRight(dim: Dimension) = setPixelMarginRight(dim.toIntPixels(metrics))

fun View.setMarginHorizontal(resId: Int) = setPixelMarginHorizontal(resources.getDimensionPixelSize(resId.assertDimensionRes()))
fun View.setMarginVertical(resId: Int) = setPixelMarginVertical(resources.getDimensionPixelSize(resId.assertDimensionRes()))
fun View.setMarginHorizontal(dim: Dimension) = setPixelMarginHorizontal(dim.toIntPixels(metrics))
fun View.setMarginVertical(dim: Dimension) = setPixelMarginVertical(dim.toIntPixels(metrics))
