/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.apppickerview.features.applabel;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.util.HashMap;
import java.util.Map;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RequiresApi(api = Build.VERSION_CODES.O)
public abstract class AppLabelMapBixbyFactory extends AbstractAppLabelMapFactory {
    public final String TAG = "AppLabelMapBixbyFactory";
    public final Context mContext;
    public final AbstractAppLabelMapFactory.KeyFormatter mKeyFormatter;

    public abstract String getAuthority();

    public AppLabelMapBixbyFactory(@NonNull Context context,
            AbstractAppLabelMapFactory.KeyFormatter keyFormatter) {
        this.mContext = context;
        this.mKeyFormatter = keyFormatter;
    }

    @NonNull
    @Override
    public Map<String, String>  getLabelMap() {
        return getLabelFromSCS();
    }


    @NonNull
    public final Map<String, String> getLabelFromSCS() {
        Cursor query = null;
        HashMap<String, String> hashMap = new HashMap<>();
        String authority = getAuthority();
        Uri withAppendedPath = Uri.withAppendedPath(Uri.parse("content://" + authority),
                "application");
        Bundle bundle = new Bundle();
        bundle.putString("android:query-arg-sql-selection", "*");
        bundle.putBoolean("query-arg-all-apps", true);
        bundle.putInt("android:query-arg-limit", 10000);
        try {
            query = this.mContext.getContentResolver().query(withAppendedPath, null, bundle, null);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        if (query == null) {
            return hashMap;
        } else if (query.moveToFirst()) {
            do {
                int columnIndex = query.getColumnIndex("label");
                int columnIndex2 = query.getColumnIndex("componentName");
                int columnIndex3 = query.getColumnIndex("packageName");
                if (columnIndex != -1 && columnIndex2 != -1 && columnIndex3 != -1) {
                    hashMap.put(this.mKeyFormatter.getKey(query.getString(columnIndex3),
                            query.getString(columnIndex2)), query.getString(columnIndex));
                }
                Log.e(this.TAG, String.format("Can't find columnIndex (%s : %d, %s : %d, %s : %d)"
                        , "label", columnIndex, "componentName",
                        columnIndex2, "packageName",
                        columnIndex3));
            } while (query.moveToNext());
            query.close();
            return hashMap;
        } else {
            query.close();
            return hashMap;
        }
    }
}