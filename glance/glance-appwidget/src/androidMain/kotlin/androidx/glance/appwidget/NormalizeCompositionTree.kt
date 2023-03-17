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
package androidx.glance.appwidget

import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.glance.BackgroundModifier
import androidx.glance.Emittable
import androidx.glance.EmittableButton
import androidx.glance.EmittableImage
import androidx.glance.EmittableWithChildren
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionModifier
import androidx.glance.action.LambdaAction
import androidx.glance.appwidget.action.CompoundButtonAction
import androidx.glance.appwidget.lazy.EmittableLazyListItem
import androidx.glance.background
import androidx.glance.extractModifier
import androidx.glance.findModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.EmittableBox
import androidx.glance.layout.HeightModifier
import androidx.glance.layout.WidthModifier
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.EmittableText
import androidx.glance.toEmittableText
import androidx.glance.unit.Dimension

internal fun normalizeCompositionTree(root: RemoteViewsRoot) {
    coerceToOneChild(root)
    root.normalizeSizes()
    root.transformTree { view ->
        if (view is EmittableLazyListItem) normalizeLazyListItem(view)
        view.transformBackgroundImageAndActionRipple()
    }
}

/**
 * Ensure that [container] has only one direct child.
 *
 * If [container] has multiple children, wrap them in an [EmittableBox] and make that the only child
 * of container. If [container] contains only children of type [EmittableSizeBox], then we will make
 * sure each of the [EmittableSizeBox]es has one child by wrapping their children in an
 * [EmittableBox].
 */
private fun coerceToOneChild(container: EmittableWithChildren) {
    if (container.children.isNotEmpty() && container.children.all { it is EmittableSizeBox }) {
        for (item in container.children) {
            item as EmittableSizeBox
            if (item.children.size == 1) continue
            val box = EmittableBox()
            box.children += item.children
            item.children.clear()
            item.children += box
        }
        return
    } else if (container.children.size == 1) {
        return
    }
    val box = EmittableBox()
    box.children += container.children
    container.children.clear()
    container.children += box
}

/**
 * Resolve mixing wrapToContent and fillMaxSize on containers.
 *
 * Make sure that if a node with wrapToContent has a child with fillMaxSize, then it becomes
 * fillMaxSize. Otherwise, the behavior depends on the version of Android.
 */
private fun EmittableWithChildren.normalizeSizes() {
    children.forEach { child ->
        if (child is EmittableWithChildren) {
            child.normalizeSizes()
        }
    }
    if ((modifier.findModifier<HeightModifier>()?.height ?: Dimension.Wrap) is Dimension.Wrap &&
        children.any { child ->
            child.modifier.findModifier<HeightModifier>()?.height is Dimension.Fill
        }
    ) {
        modifier = modifier.fillMaxHeight()
    }
    if ((modifier.findModifier<WidthModifier>()?.width ?: Dimension.Wrap) is Dimension.Wrap &&
        children.any { child ->
            child.modifier.findModifier<WidthModifier>()?.width is Dimension.Fill
        }
    ) {
        modifier = modifier.fillMaxWidth()
    }
}

/** Transform each node in the tree. */
private fun EmittableWithChildren.transformTree(block: (Emittable) -> Emittable) {
    children.forEachIndexed { index, child ->
        val newChild = block(child)
        children[index] = newChild
        if (newChild is EmittableWithChildren) newChild.transformTree(block)
    }
}

/**
 * Walks through the Emittable tree and updates the key for all LambdaActions.
 *
 * This function updates the key such that the final key is equal to the original key plus a string
 * indicating its index among its siblings. This is because sibling Composables will often have the
 * same key due to how [androidx.compose.runtime.currentCompositeKeyHash] works. Adding the index
 * makes sure that all of these keys are unique.
 *
 * Note that, because we run the same composition multiple times for different sizes in certain
 * modes (see [ForEachSize]), action keys in one SizeBox should mirror the action keys in other
 * SizeBoxes, so that if an action is triggered on the widget being displayed in one size, the state
 * will be updated for the composition in all sizes. This is why there can be multiple LambdaActions
 * for each key, even after de-duping.
 */
internal fun EmittableWithChildren.updateLambdaActionKeys(): Map<String, List<LambdaAction>> =
    children.foldIndexed(
        mutableMapOf<String, MutableList<LambdaAction>>()
    ) { index, actions, child ->
        val (action: LambdaAction?, modifiers: GlanceModifier) =
            child.modifier.extractLambdaAction()
        if (action != null && child !is EmittableSizeBox && child !is EmittableLazyListItem) {
            val newKey = action.key + "+$index"
            val newAction = LambdaAction(newKey, action.block)
            actions.getOrPut(newKey) { mutableListOf() }.add(newAction)
            child.modifier = modifiers.then(ActionModifier(newAction))
        }
        if (child is EmittableWithChildren) {
            child.updateLambdaActionKeys().forEach { (key, childActions) ->
                actions.getOrPut(key) { mutableListOf() }.addAll(childActions)
            }
        }
        actions
    }

private fun GlanceModifier.extractLambdaAction(): Pair<LambdaAction?, GlanceModifier> =
    extractModifier<ActionModifier>().let { (actionModifier, modifiers) ->
        val action = actionModifier?.action
        when {
            action is LambdaAction -> action to modifiers
            action is CompoundButtonAction && action.innerAction is LambdaAction ->
                action.innerAction to modifiers
            else -> null to modifiers
        }
    }

private fun normalizeLazyListItem(view: EmittableLazyListItem) {
    if (view.children.size == 1 && view.alignment == Alignment.CenterStart) return
    val box = EmittableBox()
    box.children += view.children
    box.contentAlignment = view.alignment
    box.modifier = view.modifier
    view.children.clear()
    view.children += box
    view.alignment = Alignment.CenterStart
}

/**
 * If this [Emittable] has a background image or a ripple, transform the emittable so that it is
 * wrapped in an [EmittableBox], with the background and ripple added as [ImageView]s in the
 * background and foreground.
 *
 * If this is an [EmittableButton], we additonally set a clip outline on the wrapper box, and
 * convert the target emittable to an [EmittableText]
 */
private fun Emittable.transformBackgroundImageAndActionRipple(): Emittable {
    // EmittableLazyListItem and EmittableSizeBox are wrappers for their immediate only child,
    // and do not get translated to their own element. We will transform their child instead.
    if (this is EmittableLazyListItem || this is EmittableSizeBox) return this

    var target = this
    val isButton = target is EmittableButton

    val shouldWrapTargetInABox = target.modifier.any {
        // Background images (i.e. BitMap or drawable resources) are emulated by placing the image
        // before the target in the wrapper box. This allows us to support content scale as well as
        // additional image features (e.g. color filters) on background images.
        (it is BackgroundModifier && it.imageProvider != null) ||
            // Ripples are implemented by placing a drawable after the target in the wrapper box.
            (it is ActionModifier && !hasBuiltinRipple()) ||
            // Buttons are implemented using a background drawable with rounded corners and an
            // EmittableText.
            isButton
    }
    if (!shouldWrapTargetInABox) return target

    // Hoisted modifiers are subtracted from the target one by one and added to the box and the
    // remaining modifiers are applied to the target.
    val boxModifiers = mutableListOf<GlanceModifier?>()
    val targetModifiers = mutableListOf<GlanceModifier?>()
    var backgroundImage: EmittableImage? = null
    var rippleImage: EmittableImage? = null

    // bgModifier.imageProvider is converted to an actual image but bgModifier.colorProvider is
    // applied back to the target. Note: We could have hoisted the bg color to box instead of
    // adding it back to the target, but for buttons, we also add an outline background to the box.
    val (bgModifier, targetModifiersMinusBg) = target.modifier.extractModifier<BackgroundModifier>()
    if (bgModifier != null) {
        if (bgModifier.imageProvider != null) {
            backgroundImage = EmittableImage().apply {
                modifier = GlanceModifier.fillMaxSize()
                provider = bgModifier.imageProvider
                contentScale = bgModifier.contentScale
            }
        } else { // is a background color modifier
            targetModifiers += bgModifier
        }
    }

    // Action modifiers are hoisted on the wrapping box and a ripple image is added to the
    // foreground if the target doesn't have it built-in.
    targetModifiersMinusBg.warnIfMultipleClickableActions()
    val (actionModifier, targetModifiersMinusAction) =
        targetModifiersMinusBg.extractModifier<ActionModifier>()
    boxModifiers += actionModifier
    if (actionModifier != null && !hasBuiltinRipple()) {
        rippleImage = EmittableImage().apply {
            modifier = GlanceModifier.fillMaxSize()
            provider = ImageProvider(R.drawable.glance_ripple)
        }
    }

    // Hoist the size and corner radius modifiers to the wrapping Box, then set the target element
    // to fill the given space.
    val (sizeAndCornerModifiers, targetModifiersMinusSizeAndCornerRadius) =
        targetModifiersMinusAction.extractSizeAndCornerRadiusModifiers()
    boxModifiers += sizeAndCornerModifiers
    targetModifiers += targetModifiersMinusSizeAndCornerRadius.fillMaxSize()

    // If this is a button, set the necessary modifiers on the wrapping Box.
    if (target is EmittableButton) {
        boxModifiers += GlanceModifier
            .clipToOutline(true)
            .enabled(target.enabled)
            .background(ImageProvider(R.drawable.glance_button_outline))
        target = target.toEmittableText()
        targetModifiers += GlanceModifier.padding(horizontal = 16.dp, vertical = 8.dp)
    }

    return EmittableBox().apply {
        modifier = boxModifiers.collect()
        if (isButton) contentAlignment = Alignment.Center

        backgroundImage?.let { children += it }
        children += target.apply { modifier = targetModifiers.collect() }
        rippleImage?.let { children += it }
    }
}

private fun Emittable.hasBuiltinRipple() =
    this is EmittableSwitch ||
    this is EmittableRadioButton ||
    this is EmittableCheckBox

private data class ExtractedSizeModifiers(
    val sizeModifiers: GlanceModifier = GlanceModifier,
    val nonSizeModifiers: GlanceModifier = GlanceModifier,
)

/**
 * Split the [GlanceModifier] into one that contains the [WidthModifier]s, [HeightModifier]s and
 * and [CornerRadiusModifier]s and one that contains the rest.
 */
private fun GlanceModifier.extractSizeAndCornerRadiusModifiers() =
    if (any { it is WidthModifier || it is HeightModifier || it is CornerRadiusModifier }) {
        foldIn(ExtractedSizeModifiers()) { acc, modifier ->
            if (modifier is WidthModifier ||
                modifier is HeightModifier ||
                modifier is CornerRadiusModifier) {
                acc.copy(sizeModifiers = acc.sizeModifiers.then(modifier))
            } else {
                acc.copy(nonSizeModifiers = acc.nonSizeModifiers.then(modifier))
            }
        }
    } else {
        ExtractedSizeModifiers(nonSizeModifiers = this)
    }

private fun GlanceModifier.warnIfMultipleClickableActions() {
    val actionCount = foldIn(0) { count, modifier ->
        if (modifier is ActionModifier) count + 1 else count
    }
    if (actionCount > 1) {
        Log.w(
            GlanceAppWidgetTag,
            "More than one clickable defined on the same GlanceModifier, " +
                "only the last one will be used."
        )
    }
}

private fun MutableList<GlanceModifier?>.collect(): GlanceModifier =
    fold(GlanceModifier) { acc: GlanceModifier, mod: GlanceModifier? ->
        mod?.let { acc.then(mod) } ?: acc
    }
