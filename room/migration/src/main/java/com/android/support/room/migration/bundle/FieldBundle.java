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

package com.android.support.room.migration.bundle;

import android.support.annotation.RestrictTo;

import com.google.gson.annotations.SerializedName;

/**
 * Data class that holds the schema information for an
 * {@link com.android.support.room.Entity Entity} field.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FieldBundle {
    @SerializedName("fieldPath")
    private String mFieldPath;
    @SerializedName("columnName")
    private String mColumnName;
    @SerializedName("affinity")
    private String mAffinity;

    public FieldBundle(String fieldPath, String columnName, String affinity) {
        mFieldPath = fieldPath;
        mColumnName = columnName;
        mAffinity = affinity;
    }

    public String getFieldPath() {
        return mFieldPath;
    }

    public String getColumnName() {
        return mColumnName;
    }

    public String getAffinity() {
        return mAffinity;
    }
}
