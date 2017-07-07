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

package android.arch.persistence.room.migration.bundle;

import android.support.annotation.RestrictTo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Data class that holds the schema information about a table Index.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class IndexBundle {
    @SerializedName("name")
    private String mName;
    @SerializedName("unique")
    private boolean mUnique;
    @SerializedName("columnNames")
    private List<String> mColumnNames;
    @SerializedName("createSql")
    private String mCreateSql;

    public IndexBundle(String name, boolean unique, List<String> columnNames,
            String createSql) {
        mName = name;
        mUnique = unique;
        mColumnNames = columnNames;
        mCreateSql = createSql;
    }

    public String getName() {
        return mName;
    }

    public boolean isUnique() {
        return mUnique;
    }

    public List<String> getColumnNames() {
        return mColumnNames;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public String create(String tableName) {
        return BundleUtil.replaceTableName(mCreateSql, tableName);
    }
}
