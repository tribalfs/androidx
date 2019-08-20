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

package androidx.paging.integration.testapp.custom

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.paging.PageLoadType
import androidx.paging.PagedList
import androidx.paging.PagedList.LoadState.Done
import androidx.paging.PagedList.LoadState.Error
import androidx.paging.PagedList.LoadState.Idle
import androidx.paging.PagedList.LoadState.Loading
import androidx.paging.PageLoadType.END
import androidx.paging.PageLoadType.REFRESH
import androidx.paging.PageLoadType.START
import androidx.paging.PagedListAdapter
import androidx.paging.integration.testapp.R
import androidx.recyclerview.widget.RecyclerView

/**
 * Sample PagedList activity with artificial data source.
 */
class PagedListSampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        val viewModel by viewModels<PagedListItemViewModel>()

        val adapter = PagedListItemAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.adapter = adapter

        viewModel.livePagedList.observe(this,
            Observer<PagedList<Item>> { adapter.submitList(it) })

        setupLoadStateButtons(viewModel, adapter)

        findViewById<Button>(R.id.button_error).setOnClickListener {
            dataSourceError.set(true)
        }
    }

    private fun setupLoadStateButtons(
        viewModel: PagedListItemViewModel,
        adapter: PagedListAdapter<Item, RecyclerView.ViewHolder>
    ) {
        val buttonStart = findViewById<Button>(R.id.button_start)
        val buttonRefresh = findViewById<Button>(R.id.button_refresh)
        val buttonEnd = findViewById<Button>(R.id.button_end)

        buttonRefresh.setOnClickListener {
            viewModel.invalidateList()
        }
        buttonStart.setOnClickListener {
            adapter.currentList?.retry()
        }
        buttonEnd.setOnClickListener {
            adapter.currentList?.retry()
        }

        adapter.addLoadStateListener { type: PageLoadType, state: PagedList.LoadState ->
            val button = when (type) {
                REFRESH -> buttonRefresh
                START -> buttonStart
                END -> buttonEnd
            }

            when (state) {
                is Idle -> {
                    button.text = "Idle"
                    button.isEnabled = type == REFRESH
                }
                is Loading -> {
                    button.text = "Loading"
                    button.isEnabled = false
                }
                is Done -> {
                    button.text = "Done"
                    button.isEnabled = false
                }
                is Error -> {
                    button.text = "Error"
                    button.isEnabled = state.retryable
                }
            }
        }
    }
}
