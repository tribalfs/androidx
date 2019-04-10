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

package androidx.paging

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@SmallTest
class RxPagedListTest {
    @JvmField
    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun observable_config() {
        val observable = dataSourceFactory.toObservable(config)
        val first = observable.blockingFirst()
        assertNotNull(first)
        assertEquals(config, first.config)
    }

    @Test
    fun observable_pageSize() {
        val observable = dataSourceFactory.toObservable(20)
        val first = observable.blockingFirst()
        assertNotNull(first)
        assertEquals(20, first.config.pageSize)
    }

    @Test
    fun flowable_config() {
        val flowable = dataSourceFactory.toFlowable(config)
        val first = flowable.blockingFirst()
        assertNotNull(first)
        assertEquals(config, first.config)
    }

    @Test
    fun flowable_pageSize() {
        val flowable = dataSourceFactory.toFlowable(20)
        val first = flowable.blockingFirst()
        assertNotNull(first)
        assertEquals(20, first.config.pageSize)
    }

    companion object {
        private val dataSource = object : PositionalDataSource<String>() {
            override fun loadInitial(
                params: LoadInitialParams,
                callback: LoadInitialCallback<String>
            ) {
                callback.onResult(listOf<String>(), 0, 0)
            }

            override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<String>) {
                // never completes...
            }
        }

        private val dataSourceFactory = object : DataSource.Factory<Int, String>() {
            override fun create(): DataSource<Int, String> {
                return dataSource
            }
        }

        private val config = Config(10)
    }
}
