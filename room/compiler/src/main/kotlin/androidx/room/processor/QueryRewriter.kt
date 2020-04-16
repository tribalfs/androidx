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

package androidx.room.processor

import androidx.room.parser.ParsedQuery
import androidx.room.solver.query.result.RowAdapter

/**
 * Interface to rewrite user queries
 */
interface QueryRewriter {
    /**
     * Rewrites the user given query. This is a place where we can run optimizations etc on the
     * user query.
     */
    fun rewrite(query: ParsedQuery, rowAdapter: RowAdapter): ParsedQuery

    companion object {
        val NoOpRewriter = object : QueryRewriter {
            override fun rewrite(query: ParsedQuery, rowAdapter: RowAdapter) = query
        }
    }
}