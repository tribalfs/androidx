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

package androidx.datastore

import java.io.InputStream
import kotlinx.coroutines.flow.Flow

/**
 * DataStore provides a safe and durable way to store small amounts of data, such as preferences
 * and application state. It does not support partial updates: if any field is modified, the whole
 * object will be serialized and persisted to disk. If you want partial updates, consider the Room
 * API (SQLite).
 *
 * DataStore provides ACID guarantees. It is thread-safe, and non-blocking. In particular, it
 * addresses these design shortcomings of the SharedPreferences API:
 *
 * 1. Synchronous API encourages StrictMode violations
 * 2. apply() and commit() have no mechanism of signalling errors
 * 3. apply() will block the UI thread on fsync()
 * 4. Not durable – it can returns state that is not yet persisted
 * 5. No consistency or transactional semantics
 * 6. Throws runtime exception on parsing errors
 * 7. Exposes mutable references to its internal state
 */
interface DataStore<T> {
    /**
     * Provides efficient, cached (when possible) access to the latest durably persisted state.
     * The flow will always either emit a value or throw an exception when attempting to read
     * from disk. If an exception is encountered, collecting again will attempt to get the data
     * again.
     *
     * Do not layer a cache on top of this API: it will be be impossible to
     * guarantee consistency. Instead, use dataFlow.first() to access a single
     * snapshot.
     *
     * @return a flow representing the current state of the data.
     */
    val dataFlow: Flow<T>

    /**
     * The serializer determines the on-disk format and API for accessing it.
     *
     * The type T MUST be immutable. Mutable types will result in broken DataStore functionality.
     *
     * TODO(b/151635324): consider changing InputStream to File.
     */
    interface Serializer<T> {

        /** Unmarshal object from stream. */
        fun readFrom(input: InputStream): T

        // TODO(b/151635324): Add writeTo() method.

        /**
         * The initial value of the serialized object. This value be returned if the file does
         * not yet exist on disk.
         */
        val defaultValue: T
    }

    // TODO(b/151635324): Add updateData() method.
    // TODO(b/151635324): Add initializers.
    // TODO(b/151635324): Add exception handlers.
    // TODO(b/151635324): Consider adding snapshot API.
}