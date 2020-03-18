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

@file:Suppress("unused")

package androidx.paging.samples

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.annotation.Sampled
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class User(
    val userId: String,
    val favoriteFood: String,
    val isFavorite: Boolean
)

// TODO: consider adding a fleshed out ViewHolder as part of the sample
@Suppress("UNUSED_PARAMETER")
class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(user: User?) {}

    companion object {
        fun create(parent: ViewGroup): UserViewHolder {
            throw NotImplementedError()
        }
    }
}

@Suppress("LocalVariableName") // We're pretending local val is global
@Sampled
fun pagingDataAdapterSample() {
    val USER_COMPARATOR = object : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            // User ID serves as unique ID
            oldItem.userId == newItem.userId

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean =
            // Compare full contents (note: Java users should call .equals())
            oldItem == newItem
    }

    class UserAdapter : PagingDataAdapter<User, UserViewHolder>(USER_COMPARATOR) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            return UserViewHolder.create(parent)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val repoItem = getItem(position)
            // Note that item may be null, ViewHolder must support binding null item as placeholder
            holder.bind(repoItem)
        }
    }
}

internal class UserPagingAdapter : BasePagingAdapter<User>()
internal class UserListViewModel : BaseViewModel<User>()

@Sampled
fun presentDataSample() {
    class MyFlowActivity : AppCompatActivity() {
        val pagingAdapter = UserPagingAdapter()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val viewModel by viewModels<UserListViewModel>()

            lifecycleScope.launch {
                viewModel.pagingFlow
                    .collectLatest { pagingData ->
                        // presentData suspends until loading this generation of data stops
                        // so be sure to use collectLatest {} when presenting a Flow<PagingData>
                        pagingAdapter.presentData(pagingData)
                    }
            }
        }
    }
}

@Sampled
fun submitDataLiveDataSample() {
    class MyLiveDataActivity : AppCompatActivity() {
        val pagingAdapter = UserPagingAdapter()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val viewModel by viewModels<UserListViewModel>()

            viewModel.pagingLiveData.observe(this) { pagingData ->
                pagingAdapter.submitData(lifecycle, pagingData)
            }
        }
    }
}

fun <T> Flowable<T>.autoDispose(
    @Suppress("UNUSED_PARAMETER") scope: AppCompatActivity
): Flowable<T> {
    throw NotImplementedError()
}

@Sampled
fun submitDataRxSample() {
    class MyRxJava2Activity : AppCompatActivity() {
        val pagingAdapter = UserPagingAdapter()
        val disposable = CompositeDisposable()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val viewModel by viewModels<UserListViewModel>()

            viewModel.pagingFlowable
                .autoDispose(this) // Using AutoDispose to handle subscription lifecycle
                .subscribe { pagingData ->
                    pagingAdapter.submitData(lifecycle, pagingData)
                }
        }
    }
}