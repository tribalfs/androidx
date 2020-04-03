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

package androidx.hilt.integration.viewmodelapp

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.ActivityViewModelFactory
import androidx.hilt.lifecycle.ViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SimpleActivity : FragmentActivity() {

    // TODO(danysantiago): Should be declared in Hilt gen class
    @Inject
    @ActivityViewModelFactory
    lateinit var viewModelFactory: ViewModelFactory

    val simpleViewModel by viewModels<SimpleViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        Log.d("SimpleActivity", simpleViewModel.hi())

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.container, SimpleFragment())
                .commit()
        }
    }

    // TODO(danysantiago): Should be overridden by Hilt gen class
    override fun getDefaultViewModelProviderFactory() = viewModelFactory
}