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

package androidx.apppickerview.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
class DataManager {
    private static final String TAG = "DataManager";

    private static final int MAX_APP_LIST_COUNT = 10000;

    private static final Uri APP_LIST_PROVIDER_CONTENT_URI
            = Uri.parse("content://com.samsung.android.settings.applist");
    private static final String KEY_APP_LABEL = "app_title";
    private static final String KEY_PACKAGE_NAME = "package_name";

    private static final boolean sIsSupportQUERY = Build.VERSION.SDK_INT >= 26;
    private static final boolean sIsSupportSCS = Build.VERSION.SDK_INT > 29;

    DataManager() {
    }

    public static List<AppPickerView.AppLabelInfo> resetPackages(Context context,
            List<String> list) {
        return resetPackages(context, list, null, null);
    }

    public static List<AppPickerView.AppLabelInfo> resetPackages(List<ComponentName> list,
            Context context) {
        return resetPackages(context, null, null, list);
    }

    public static List<AppPickerView.AppLabelInfo> resetPackages(Context context,
            List<String> packageNamesList,
            List<AppPickerView.AppLabelInfo> appLabelInfoList,
            List<ComponentName> componentNameList) {
        HashMap<String, String> appLabelMap;
        boolean isComponentListNotNull = componentNameList != null;
        HashMap<String, String> labelFromSettingsOrSCS = sIsSupportQUERY
                ? getLabelFromSCS(context, isComponentListNotNull)
                : isComponentListNotNull ? null : loadLabelFromSettings(context);

        if (appLabelInfoList != null) {
            appLabelMap = new HashMap<>();
            for (AppPickerView.AppLabelInfo appLabelInfo : appLabelInfoList) {
                String packageName = appLabelInfo.getPackageName();
                if (appLabelInfo.getActivityName() != null
                        && !appLabelInfo.getActivityName().isEmpty()) {
                    packageName = packageName + "/" + appLabelInfo.getActivityName();
                }
                appLabelMap.put(packageName, appLabelInfo.getLabel());
            }
        } else {
            appLabelMap = null;
        }

        ArrayList<AppPickerView.AppLabelInfo> updatedLabelInfoList = new ArrayList<>();

        if (isComponentListNotNull) {
            for (ComponentName componentName : componentNameList) {
                String packageAndActivityName = componentName.getPackageName() + "/" + componentName.getClassName();
                String appLabel = appLabelMap != null ? appLabelMap.get(packageAndActivityName) : null;
                if (appLabel == null && labelFromSettingsOrSCS != null) {
                    appLabel = labelFromSettingsOrSCS.get(packageAndActivityName);
                }
                if (appLabel == null) {
                    appLabel = getLabelFromPackageManager(context, componentName);
                }
                updatedLabelInfoList.add(
                        new AppPickerView.AppLabelInfo(componentName.getPackageName(),
                                appLabel, componentName.getClassName()));
            }
        } else {
            for (String packageName : packageNamesList) {
                String label = appLabelMap != null ? appLabelMap.get(packageName) : null;
                if (label == null && labelFromSettingsOrSCS != null) {
                    label = labelFromSettingsOrSCS.get(packageName);
                }
                if (label == null) {
                    label = getLabelFromPackageManager(context, packageName);
                }
                updatedLabelInfoList.add(new AppPickerView.AppLabelInfo(packageName, label, ""));
            }
        }
        return updatedLabelInfoList;
    }

    @SuppressLint("Range")
    private static HashMap<String, String> loadLabelFromSettings(Context context) {
        Cursor query = context.getContentResolver().query(APP_LIST_PROVIDER_CONTENT_URI,
                null, null, null, null);
        HashMap<String, String> appLabelMap = new HashMap<>();
        if (query != null && query.moveToFirst()) {
            do {
                appLabelMap.put(query.getString(query.getColumnIndex(KEY_PACKAGE_NAME)),
                        query.getString(query.getColumnIndex(KEY_APP_LABEL)));
            } while (query.moveToNext());
            query.close();
        }
        return appLabelMap;
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("Range")
    private static HashMap<String, String> getLabelFromSCS(Context context, boolean isComponentNameIncluded) {
        HashMap<String, String> appLabelMap = new HashMap<>();
        Cursor cursor = null;

        try {
            Uri contentUri = Uri.withAppendedPath(Uri.parse("content://"
                            + (sIsSupportSCS ?
                            "com.samsung.android.scs.ai.search/v1"
                            : "com.samsung.android.bixby.service.bixbysearch/v1")),
                    "application");

            Bundle queryArgs = new Bundle();
            queryArgs.putString("android:query-arg-sql-selection", "*");
            queryArgs.putBoolean("query-arg-all-apps", true);
            queryArgs.putInt("android:query-arg-limit", MAX_APP_LIST_COUNT);

            cursor = context.getContentResolver().query(contentUri, null, queryArgs, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String appLabel = cursor.getString(cursor.getColumnIndex("label"));
                    String packageName;

                    if (isComponentNameIncluded) {
                        packageName = cursor.getString(cursor.getColumnIndex("componentName"))
                                + "/" + cursor.getString(cursor.getColumnIndex("packageName"));
                    } else {
                        packageName = cursor.getString(cursor.getColumnIndex("packageName"));
                    }

                    appLabelMap.put(packageName, appLabel);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return appLabelMap;
    }

    private static String getLabelFromPackageManager(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return applicationInfo != null ?
                    (String) packageManager.getApplicationLabel(applicationInfo) : "Unknown";
        } catch (PackageManager.NameNotFoundException unused) {
            Log.i(TAG, "can't find label for " + packageName);
            return "Unknown";
        }
    }

    private static String getLabelFromPackageManager(Context context, ComponentName componentName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            ActivityInfo activityInfo = packageManager.getActivityInfo(componentName, 0);
            return activityInfo != null ?
                    activityInfo.loadLabel(packageManager).toString() : "Unknown";
        } catch (PackageManager.NameNotFoundException unused) {
            Log.i(TAG, "can't find label for " + componentName);
            return "Unknown";
        }
    }
}
