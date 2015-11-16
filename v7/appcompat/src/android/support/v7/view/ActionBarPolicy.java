/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.support.v7.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v7.appcompat.R;
import android.view.ViewConfiguration;

/**
 * Allows components to query for various configuration policy decisions about how the action bar
 * should lay out and behave on the current device.
 *
 * @hide
 */
public class ActionBarPolicy {

    private Context mContext;

    public static ActionBarPolicy get(Context context) {
        return new ActionBarPolicy(context);
    }

    private ActionBarPolicy(Context context) {
        mContext = context;
    }

    /**
     * Returns the maximum number of action buttons that should be permitted within an action
     * bar/action mode. This will be used to determine how many showAsAction="ifRoom" items can fit.
     * "always" items can override this.
     */
    public int getMaxActionButtons() {
        final Configuration config = mContext.getResources().getConfiguration();
        final int width = config.screenWidthDp;
        final int height = config.screenHeightDp;
        final int smallest;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            smallest = config.smallestScreenWidthDp;
        } else {
            smallest = 0;
        }
        if (smallest > 600 || width > 600 || (width > 960 && height > 720)
                || (width > 720 && height > 960)) {
            // For values-w600dp, values-sw600dp and values-xlarge.
            return 5;
        } else if (width >= 500 || (width > 640 && height > 480) || (width > 480 && height > 640)) {
            // For values-w500dp and values-large.
            return 4;
        } else if (width >= 360) {
            // For values-w360dp.
            return 3;
        } else {
            return 2;
        }
    }

    public boolean showsOverflowMenuButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return true;
        } else {
            return !ViewConfigurationCompat.hasPermanentMenuKey(ViewConfiguration.get(mContext));
        }
    }

    public int getEmbeddedMenuWidthLimit() {
        return mContext.getResources().getDisplayMetrics().widthPixels / 2;
    }

    public boolean hasEmbeddedTabs() {
        final int targetSdk = mContext.getApplicationInfo().targetSdkVersion;
        if (targetSdk >= Build.VERSION_CODES.JELLY_BEAN) {
            return mContext.getResources().getBoolean(R.bool.abc_action_bar_embed_tabs);
        }

        // The embedded tabs policy changed in Jellybean; give older apps the old policy
        // so they get what they expect.
        return mContext.getResources().getBoolean(R.bool.abc_action_bar_embed_tabs_pre_jb);
    }

    public int getTabContainerHeight() {
        TypedArray a = mContext.obtainStyledAttributes(null, R.styleable.ActionBar,
                R.attr.actionBarStyle, 0);
        int height = a.getLayoutDimension(R.styleable.ActionBar_height, 0);
        Resources r = mContext.getResources();
        if (!hasEmbeddedTabs()) {
            // Stacked tabs; limit the height
            height = Math.min(height,
                    r.getDimensionPixelSize(R.dimen.abc_action_bar_stacked_max_height));
        }
        a.recycle();
        return height;
    }

    public boolean enableHomeButtonByDefault() {
        // Older apps get the home button interaction enabled by default.
        // Newer apps need to enable it explicitly.
        return mContext.getApplicationInfo().targetSdkVersion <
                Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public int getStackedTabMaxWidth() {
        return mContext.getResources().getDimensionPixelSize(
                R.dimen.abc_action_bar_stacked_tab_max_width);
    }
}
