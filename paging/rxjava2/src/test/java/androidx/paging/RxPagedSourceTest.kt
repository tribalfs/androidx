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

package androidx.paging

import androidx.paging.PagedSource.LoadResult.Page
import io.reactivex.Single
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(JUnit4::class)
class RxPagedSourceTest {
    private fun loadInternal(params: PagedSource.LoadParams<Int>): Page<Int, Int> {
        val key = params.key!! // Intentionally fail on null keys
        return Page(
            List(params.loadSize) { it + key },
            prevKey = key - params.loadSize,
            nextKey = key + params.loadSize
        )
    }

    private val pagedSource = object : PagedSource<Int, Int>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
            return loadInternal(params)
        }
    }

    private val rxPagedSource = object : RxPagedSource<Int, Int>() {
        override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Int>> {
            return Single.create { emitter ->
                emitter.onSuccess(loadInternal(params))
            }
        }
    }

    @Test
    fun basic() = runBlocking {
        val params = PagedSource.LoadParams(LoadType.REFRESH, 0, 2, false, 2)
        assertEquals(pagedSource.load(params), rxPagedSource.load(params))
    }

    @Test
    fun error() {
        runBlocking {
            val params = PagedSource.LoadParams<Int>(LoadType.REFRESH, null, 2, false, 2)
            assertFailsWith<NullPointerException> { pagedSource.load(params) }
            assertFailsWith<NullPointerException> { rxPagedSource.load(params) }
        }
    }
}