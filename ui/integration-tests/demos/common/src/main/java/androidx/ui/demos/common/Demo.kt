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

package androidx.ui.demos.common

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.Composable
import kotlin.reflect.KClass

/**
 * Generic demo with a [title] that will be displayed in the list of demos.
 */
sealed class Demo(val title: String)

/**
 * Demo that launches an [Activity] when selected.
 *
 * This should only be used for demos that need to customize the activity, the large majority of
 * demos should just use [ComposableDemo] instead.
 *
 * @property activityClass the KClass (Foo::class) of the activity that will be launched when
 * this demo is selected.
 */
class ActivityDemo<T : ComponentActivity>(title: String, val activityClass: KClass<T>) : Demo(title)

/**
 * Demo that displays [Composable] [content] when selected.
 */
class ComposableDemo(title: String, val content: @Composable() () -> Unit) : Demo(title)

/**
 * A category of [Demo]s, that will display a list of [demos] when selected.
 */
class DemoCategory(title: String, val demos: List<Demo>) : Demo(title)

/**
 * Flattened recursive [List] of every launchable demo in [this].
 */
fun DemoCategory.allLaunchableDemos(): List<Demo> {
    val demos = mutableListOf<Demo>()
    fun DemoCategory.addAllDemos() {
        val (categories, launchableDemos) = this.demos.partition { it is DemoCategory }
        categories.forEach { (it as DemoCategory).addAllDemos() }
        demos.addAll(launchableDemos)
    }
    addAllDemos()
    return demos
}
