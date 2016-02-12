/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.graphics.drawable;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

/**
 * Internal common delegation shared by VectorDrawableCompat and AnimatedVectorDrawableCompat
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
abstract class VectorDrawableCommon extends Drawable {
    /**
     * Obtains styled attributes from the theme, if available, or unstyled
     * resources if the theme is null.
     */
    static TypedArray obtainAttributes(
            Resources res, Resources.Theme theme, AttributeSet set, int[] attrs) {
        if (theme == null) {
            return res.obtainAttributes(set, attrs);
        }
        return theme.obtainStyledAttributes(set, attrs, 0, 0);
    }

    // Drawable delegation for Lollipop and above.
    Drawable mDelegateDrawable;

    @Override
    public void setColorFilter(int color, PorterDuff.Mode mode) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setColorFilter(color, mode);
            return;
        }
        super.setColorFilter(color, mode);
    }

    @Override
    public ColorFilter getColorFilter() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getColorFilter();
        }
        return super.getColorFilter();
    }

    @Override
    protected boolean onLevelChange(int level) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setLevel(level);
        }
        return super.onLevelChange(level);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setBounds(bounds);
            return;
        }
        super.onBoundsChange(bounds);
    }

    @Override
    public void setHotspot(float x, float y) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setHotspot(x, y);
        }
        return;
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setHotspotBounds(left, top, right, bottom);
            return;
        }
    }

    @TargetApi(23)
    @Override
    public void getHotspotBounds(Rect outRect) {
        // API >= 21 only.
        if (mDelegateDrawable != null && Build.VERSION.SDK_INT >= 23) {
            mDelegateDrawable.getHotspotBounds(outRect);
            return;
        }
    }

    @Override
    public Rect getDirtyBounds() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getBounds();
        }
        return super.getDirtyBounds();
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setFilterBitmap(filter);
            return;
        }
    }

    @TargetApi(23)
    @Override
    public boolean isFilterBitmap() {
        // API >= 23 only
        if (mDelegateDrawable != null && Build.VERSION.SDK_INT >= 23) {
            return mDelegateDrawable.isFilterBitmap();
        }
        return false;
    }

    @Override
    public void getOutline(Outline outline) {
        // API >= 21 only
        if (mDelegateDrawable != null) {
            mDelegateDrawable.getOutline(outline);
            return;
        }
    }

    @Override
    public void jumpToCurrentState() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.jumpToCurrentState();
            return;
        }
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setAutoMirrored(mirrored);
            return;
        }
    }

    @Override
    public boolean isAutoMirrored() {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.isAutoMirrored();
        }
        return false;
    }

    @Override
    public void applyTheme(Resources.Theme t) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            mDelegateDrawable.applyTheme(t);
            return;
        }
    }

    @TargetApi(23)
    @Override
    public int getLayoutDirection() {
        if (mDelegateDrawable != null && Build.VERSION.SDK_INT >= 23) {
            return mDelegateDrawable.getLayoutDirection();
        }
        return View.LAYOUT_DIRECTION_LTR;
    }

    @TargetApi(23)
    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        if (mDelegateDrawable != null && Build.VERSION.SDK_INT >= 23) {
            return mDelegateDrawable.onLayoutDirectionChanged(layoutDirection);
        }
        return false;
    }

    @Override
    public void clearColorFilter() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.clearColorFilter();
            return;
        }
        super.clearColorFilter();
    }

    @Override
    public Drawable getCurrent() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getCurrent();
        }
        return super.getCurrent();
    }

    @Override
    public int getMinimumWidth() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getMinimumWidth();
        }
        return super.getMinimumWidth();
    }

    @Override
    public int getMinimumHeight() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getMinimumHeight();
        }
        return super.getMinimumHeight();
    }

    @Override
    public boolean getPadding(Rect padding) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getPadding(padding);
        }
        return super.getPadding(padding);
    }

    @Override
    public int[] getState() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getState();
        }
        return super.getState();
    }


    @Override
    public Region getTransparentRegion() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getTransparentRegion();
        }
        return super.getTransparentRegion();
    }

    @Override
    public void setChangingConfigurations(int configs) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setChangingConfigurations(configs);
            return;
        }
        super.setChangingConfigurations(configs);
    }

    @Override
    public boolean setState(int[] stateSet) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setState(stateSet);
        }
        return super.setState(stateSet);
    }
}
