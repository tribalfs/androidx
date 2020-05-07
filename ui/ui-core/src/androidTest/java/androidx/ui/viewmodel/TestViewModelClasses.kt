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

package androidx.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

val viewModelClasses = arrayOf(
    SimpleViewModel::class.java,
    ApplicationViewModel::class.java,
    SavedStateHandleViewModel::class.java,
    SavedStateHandleAndApplicationViewModel::class.java
)

class SimpleViewModel : ViewModel()

class SavedStateHandleViewModel(@Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle) :
    ViewModel()

class ApplicationViewModel(application: Application) : AndroidViewModel(application)

class SavedStateHandleAndApplicationViewModel(
    application: Application,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle
) : AndroidViewModel(application)