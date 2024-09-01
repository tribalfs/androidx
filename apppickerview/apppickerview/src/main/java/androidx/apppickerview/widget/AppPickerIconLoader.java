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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.reflect.app.SeslApplicationPackageManagerReflector;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

public class AppPickerIconLoader {
    private static final String THREAD_NAME = "AppPickerIconLoader";
    private PackageManager mPackageManager;
    private LoadIconTask mLoadIconTask;

    public AppPickerIconLoader(@NonNull Context context) {
        mPackageManager = context.getPackageManager();
    }

    public void loadIcon(@NonNull String packageName, @Nullable String activityName,
            @NonNull ImageView imageView) {
        if (!TextUtils.isEmpty(packageName)) {
            imageView.setTag(packageName);

            IconInfo iconInfo = new IconInfo(packageName, activityName, imageView);
            new LoadIconTask(iconInfo)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    Drawable getAppIcon(String packageName, String activityName) {
        if (activityName != null && !activityName.isEmpty()) {
            ComponentName componentName
                    = new ComponentName(packageName, activityName);

            Drawable appIcon
                    = SeslApplicationPackageManagerReflector
                    .semGetActivityIconForIconTray(mPackageManager, componentName, 1);

            if (appIcon != null) {
                return appIcon;
            }

            try {
                return mPackageManager.getActivityIcon(componentName);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        } else {
            Drawable appIcon
                    = SeslApplicationPackageManagerReflector
                    .semGetApplicationIconForIconTray(mPackageManager, packageName, 1);

            if (appIcon != null) {
                return appIcon;
            }

            try {
                return mPackageManager.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }

    public void startIconLoaderThread() {
    }

    public void stopIconLoaderThread() {
    }

    private static class IconInfo {
        String activityName;
        Drawable drawable = null;
        ImageView imageView;
        String packageName;

        public IconInfo(String packageName, String activityName,
                        ImageView imageView) {
            this.packageName = packageName;
            this.imageView = imageView;
            this.activityName = activityName;
        }
    }

    class LoadIconTask extends AsyncTask<Void, Void, Drawable> {
        private final IconInfo mIconInfo;

        LoadIconTask(IconInfo iconInfo) {
            mIconInfo = iconInfo;
        }

        protected Drawable doInBackground(Void... params) {
            return getAppIcon(mIconInfo.packageName, mIconInfo.activityName);
        }

        protected void onPostExecute(Drawable result) {
            if (mIconInfo != null) {
                if (mIconInfo.imageView != null && result != null) {
                    mIconInfo.imageView.setImageDrawable(result);
                }
            }
        }
    }
}
