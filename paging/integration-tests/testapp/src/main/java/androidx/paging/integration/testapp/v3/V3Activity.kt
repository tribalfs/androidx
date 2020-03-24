/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.paging.integration.testapp.v3

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.LoadState.Error
import androidx.paging.LoadState.Loading
import androidx.paging.LoadType
import androidx.paging.PagingDataAdapter
import androidx.paging.integration.testapp.R
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class V3Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_recycler_view)
        val viewModel by viewModels<V3ViewModel>()

        val pagingAdapter = V3Adapter()
        val orientationText = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "land"
            Configuration.ORIENTATION_PORTRAIT -> "port"
            else -> "unknown"
        }
        // NOTE: lifecycleScope means we don't respect paused state here
        lifecycleScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            viewModel.flow
                .map { pagingData ->
                    pagingData.map { it.copy(text = "${it.text} - $orientationText") }
                }
                .collectLatest { pagingAdapter.presentData(it) }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.adapter = pagingAdapter.withLoadStateHeaderAndFooter(
            header = StateItemAdapter { pagingAdapter.retry() },
            footer = StateItemAdapter { pagingAdapter.retry() }
        )

        setupLoadStateButtons(pagingAdapter)

        findViewById<Button>(R.id.button_error).setOnClickListener {
            dataSourceError.set(true)
        }
    }

    private fun setupLoadStateButtons(adapter: PagingDataAdapter<Item, RecyclerView.ViewHolder>) {
        val button = findViewById<Button>(R.id.button_refresh)
        adapter.addLoadStateListener { type: LoadType, state: LoadState ->
            if (type != LoadType.REFRESH) return@addLoadStateListener

            when (state) {
                is LoadState.Idle -> {
                    button.text = "Refresh"
                    button.isEnabled = true
                    button.setOnClickListener {
                        adapter.refresh()
                    }
                }
                is Loading -> {
                    button.text = "Loading"
                    button.isEnabled = false
                }
                is LoadState.Done -> {
                    button.text = "Done"
                    button.isEnabled = false
                }
                is Error -> {
                    button.text = "Error"
                    button.isEnabled = true
                    button.setOnClickListener {
                        adapter.retry()
                    }
                }
            }
        }
    }
}