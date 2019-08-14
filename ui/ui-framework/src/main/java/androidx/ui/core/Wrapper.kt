/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.ui.core

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.ui.core.input.FocusManager
import androidx.ui.input.TextInputService
import androidx.compose.Ambient
import androidx.compose.composer
import androidx.compose.Composable
import androidx.compose.Compose
import androidx.compose.CompositionContext
import androidx.compose.CompositionReference
import androidx.compose.Observe
import androidx.compose.ambient
import androidx.compose.compositionReference
import androidx.compose.effectOf
import androidx.compose.memo
import androidx.compose.onPreCommit
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.ui.autofill.Autofill
import androidx.ui.autofill.AutofillTree
import androidx.ui.core.text.AndroidFontResourceLoader
import androidx.ui.text.font.Font
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Composes a view containing ui composables into a view composition.
 * <p>
 * This is supposed to be used only in view compositions. If compose ui is supposed to be the root of composition use
 * [Activity.setContent] or [ViewGroup.setContent] extensions.
 */
@Composable
fun ComposeView(children: @Composable() () -> Unit) {
    val rootRef = +memo { Ref<AndroidCraneView>() }

    <AndroidCraneView ref=rootRef>
        var reference: CompositionReference? = null
        var cc: CompositionContext? = null

        // This is a temporary solution until we get proper subcomposition APIs in place.
        // Right now, we want to enforce a sort of "depth-first" ordering of recompositions,
        // even when they happen across composition contexts. When we do "subcomposition",
        // like we are doing here, that means for every invalidation of the child context, we
        // need to invalidate the scope of the parent reference, and wait for it to recompose
        // the child. The Observe is put in place here to ensure that the scope around the
        // reference we are using is as small as possible, and, in particular, does not include
        // the composition of `children()`. This means that we are using the nullability of `cc`
        // to determine if the CraneWrapper in general is getting recomposed, or if its just
        // the invalidation scope of the Observe. If it's the latter, we just want to call
        // `cc.recomposeSync()` which will only recompose the invalidations in the child context,
        // which means it *will not* call `children()` again if it doesn't have to.
        Observe {
            reference = +compositionReference()
            cc?.recomposeSync()
            +onPreCommit(true) {
                onDispose {
                    rootRef.value?.let {
                        val layoutRootNode = it.root
                        val context = it.context
                        Compose.disposeComposition(layoutRootNode, context)
                    }
                }
            }
        }
        val rootLayoutNode = rootRef.value?.root ?: error("Failed to create root platform view")
        val context = rootRef.value?.context ?: composer.composer.context

        // If this value is inlined where it is used, an error that includes 'Precise Reference:
        // kotlinx.coroutines.Dispatchers' not instance of 'Precise Reference: androidx.compose.Ambient'.
        val coroutineContext = Dispatchers.Main
        cc = Compose.composeInto(container = rootLayoutNode, context = context, parent = reference) {
            WrapWithAmbients(rootRef.value!!, context, coroutineContext) {
                children()
            }
        }
    </AndroidCraneView>
}

/**
 * Composes the given composable into the given activity. The composable will become the root view
 * of the given activity.
 *
 * @param content Composable that will be the content of the activity.
 */
fun Activity.setContent(
    content: @Composable() () -> Unit
): CompositionContext? {
    val craneView = window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
        .getChildAt(0) as? AndroidCraneView
        ?: AndroidCraneView(this).also { setContentView(it) }

    // If this value is inlined where it is used, an error that includes 'Precise Reference:
    // kotlinx.coroutines.Dispatchers' not instance of 'Precise Reference: androidx.compose.Ambient'.
    val coroutineContext = Dispatchers.Main
    return Compose.composeInto(craneView.root, this) {
        WrapWithAmbients(craneView, this, coroutineContext) {
            content()
        }
    }
}

/**
 * Composes the given composable into the given view.
 *
 * @param content Composable that will be the content of the view.
 */
fun ViewGroup.setContent(
    content: @Composable() () -> Unit
): CompositionContext? {
    val craneView =
        if (childCount > 0) { getChildAt(0) as? AndroidCraneView } else { removeAllViews(); null }
        ?: AndroidCraneView(context).also { addView(it) }

    // If this value is inlined where it is used, an error that includes 'Precise Reference:
    // kotlinx.coroutines.Dispatchers' not instance of 'Precise Reference: androidx.compose.Ambient'.
    val coroutineContext = Dispatchers.Main
    return Compose.composeInto(craneView.root, context) {
        WrapWithAmbients(craneView, context, coroutineContext) {
            content()
        }
    }
}

@Composable
private fun WrapWithAmbients(
    craneView: AndroidCraneView,
    context: Context,
    coroutineContext: CoroutineContext,
    content: @Composable() () -> Unit
) {
    // TODO(nona): Tie the focus manger lifecycle to Window, otherwise FocusManager won't work
    //             with nested AndroidCraneView case
    val focusManager = +memo { FocusManager() }
    val configuration = +state { context.applicationContext.resources.configuration }

    // We don't use the attached View's layout direction here since that layout direction may not
    // be resolved since the widgets may be composed without attaching to the RootViewImpl.
    // In Jetpack Compose, use the locale layout direction (i.e. layoutDirection came from
    // configuration) as a default layout direction.
    val layoutDirection = when(configuration.value.layoutDirection) {
        android.util.LayoutDirection.LTR -> LayoutDirection.Ltr
        android.util.LayoutDirection.RTL -> LayoutDirection.Rtl
        // API doc says Configuration#getLayoutDirection only returns LTR or RTL.
        // Fallback to LTR for unexpected return value.
        else -> LayoutDirection.Ltr
    }
    +memo {
        craneView.configurationChangeObserver = {
            // onConfigurationChange is the correct hook to update configuration, however it is
            // possible that the configuration object itself may come from a wrapped
            // context / themed activity, and may not actually reflect the system. So instead we
            // use this hook to grab the applicationContext's configuration, which accurately
            // reflects the state of the application / system.
            configuration.value = context.applicationContext.resources.configuration
        }
    }

    ContextAmbient.Provider(value = context) {
        CoroutineContextAmbient.Provider(value = coroutineContext) {
            DensityAmbient.Provider(value = Density(context)) {
                FocusManagerAmbient.Provider(value = focusManager) {
                    TextInputServiceAmbient.Provider(value = craneView.textInputService) {
                        FontLoaderAmbient.Provider(value = AndroidFontResourceLoader(context)) {
                            AutofillTreeAmbient.Provider(value = craneView.autofillTree) {
                                AutofillAmbient.Provider(value = craneView.autofill) {
                                    ConfigurationAmbient.Provider(value = configuration.value) {
                                        LayoutDirectionAmbient.Provider(value = layoutDirection) {
                                            content()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

val ContextAmbient = Ambient.of<Context>()

val DensityAmbient = Ambient.of<Density>()

val CoroutineContextAmbient = Ambient.of<CoroutineContext>()

val ConfigurationAmbient = Ambient.of<Configuration>()

val AutofillAmbient = Ambient.of<Autofill?>()

// This will ultimately be replaced by Autofill Semantics (b/138604305).
val AutofillTreeAmbient = Ambient.of<AutofillTree>()

val LayoutDirectionAmbient = Ambient.of<LayoutDirection>()

internal val FocusManagerAmbient = Ambient.of<FocusManager>()

internal val TextInputServiceAmbient = Ambient.of<TextInputService?>()

internal val FontLoaderAmbient = Ambient.of<Font.ResourceLoader>()

/**
 * [ambient] to get a [Density] object from an internal [DensityAmbient].
 *
 * Note: this is an experiment with the ways to achieve a read-only public [Ambient]s.
 */
@CheckResult(suggest = "+")
fun ambientDensity() =
    effectOf<Density> { +ambient(DensityAmbient) }

/**
 * An effect to be able to convert dimensions between each other.
 * A [Density] object will be taken from an ambient.
 *
 * Usage examples:
 *
 *     +withDensity() {
 *        val pxHeight = DpHeight.toPx()
 *     }
 *
 * or
 *
 *     val pxHeight = +withDensity(density) { DpHeight.toPx() }
 *
 */
@CheckResult(suggest = "+")
// can't make this inline as tests are failing with "DensityKt.$jacocoInit()' is inaccessible"
/*inline*/ fun <R> withDensity(/*crossinline*/ block: DensityReceiver.() -> R) =
    effectOf<R> {
        @Suppress("USELESS_CAST")
        withDensity(+ambientDensity(), block as DensityReceiver.() -> R)
    }

/**
 * A component to be able to convert dimensions between each other.
 * A [Density] object will be take from an ambient.
 *
 * Usage example:
 *   WithDensity {
 *     Draw() { canvas, _ ->
 *       canvas.drawRect(Rect(0, 0, dpHeight.toPx(), dpWidth.toPx()), paint)
 *     }
 *   }
 */
@Composable
fun WithDensity(block: @Composable DensityReceiver.() -> Unit) {
    DensityReceiverImpl(+ambientDensity()).block()
}
