/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.sqliteMultiplatform

import androidx.annotation.RestrictTo

fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

fun <R> SQLiteStatement.use(block: (SQLiteStatement) -> R): R {
    try {
        return block.invoke(this)
    } finally {
        close()
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun throwSQLiteException(errorCode: Int, errorMsg: String?): Nothing {
    val message = buildString {
        append("Error code: $errorCode")
        if (errorMsg != null) {
            append(", message: $errorMsg")
        }
    }
    throw SQLiteException(message)
}
