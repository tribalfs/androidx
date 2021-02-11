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

package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.viewbinding.ViewBinding

/**
 * Composes an Android layout resource in the presence of [ViewBinding]. The binding is obtained
 * from the [factory] block, which will be called exactly once to obtain the [ViewBinding]
 * to be composed, and it is also guaranteed to be invoked on the UI thread.
 * Therefore, in addition to creating the [ViewBinding], the block can also be used
 * to perform one-off initializations and [View] constant properties' setting.
 * The [update] block can be run multiple times (on the UI thread as well) due to recomposition,
 * and it is the right place to set [View] properties depending on state. When state changes,
 * the block will be reexecuted to set the new properties. Note the block will also be ran once
 * right after the [factory] block completes.
 *
 * @sample androidx.compose.ui.samples.AndroidViewBindingSample
 *
 * @param factory The block creating the [ViewBinding] to be composed.
 * @param modifier The modifier to be applied to the layout.
 * @param update The callback to be invoked after the layout is inflated.
 */
@Composable
fun <T : ViewBinding> AndroidViewBinding(
    factory: (inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean) -> T,
    modifier: Modifier = Modifier,
    update: T.() -> Unit = {}
) {
    val viewBindingRef = remember { Ref<T>() }
    val viewBlock: (Context) -> View = remember {
        { context ->
            val inflater = LayoutInflater.from(context)
            val viewBinding = factory(inflater, FrameLayout(context), false)
            viewBindingRef.value = viewBinding
            viewBinding.root
        }
    }
    AndroidView(
        factory = viewBlock,
        modifier = modifier,
        update = { viewBindingRef.value?.update() }
    )
}
