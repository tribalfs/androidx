/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room.migration.bundle

import androidx.annotation.RestrictTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Data class that holds the schema information about a [androidx.room.PrimaryKey]. */
@Serializable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PrimaryKeyBundle(
    @SerialName("autoGenerate") val isAutoGenerate: Boolean,
    @SerialName("columnNames") val columnNames: List<String>
) : SchemaEquality<PrimaryKeyBundle> {

    override fun isSchemaEqual(other: PrimaryKeyBundle): Boolean {
        return columnNames == other.columnNames && isAutoGenerate == other.isAutoGenerate
    }
}
