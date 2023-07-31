/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.lifecycle.observers

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver

@Suppress("DEPRECATION")
interface Interface2 : LifecycleObserver {
    @androidx.lifecycle.OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate()

    @androidx.lifecycle.OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy()
}
