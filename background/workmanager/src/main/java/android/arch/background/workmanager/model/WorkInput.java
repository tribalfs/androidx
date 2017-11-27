/*
 * Copyright 2017 The Android Open Source Project
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

package android.arch.background.workmanager.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.support.annotation.NonNull;

/**
 * Database entity that defines a mapping from a {@link WorkSpec} id to an {@link Arguments} object.
 */
@Entity(foreignKeys = {
        @ForeignKey(
                entity = WorkSpec.class,
                parentColumns = "id",
                childColumns = "work_spec_id",
                onDelete = ForeignKey.CASCADE,
                onUpdate = ForeignKey.CASCADE)},
        primaryKeys = { "work_spec_id", "arguments" })
public class WorkInput {

    @NonNull
    @ColumnInfo(name = "work_spec_id")
    String mWorkSpecId;

    @NonNull
    @ColumnInfo(name = "arguments")
    Arguments mArguments;

    public WorkInput(@NonNull String workSpecId, @NonNull Arguments arguments) {
        mWorkSpecId = workSpecId;
        mArguments = arguments;
    }
}
