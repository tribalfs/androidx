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

package androidx.app.slice.widget;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.support.annotation.RestrictTo;

import androidx.app.slice.core.SliceQuery;
import androidx.app.slice.view.R;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ShortcutView extends androidx.app.slice.widget.SliceView.SliceModeView {

    private static final String TAG = "ShortcutView";

    private Uri mUri;
    private PendingIntent mAction;
    private SliceItem mLabel;
    private SliceItem mIcon;

    private int mLargeIconSize;
    private int mSmallIconSize;

    public ShortcutView(Context context) {
        super(context);
        final Resources res = getResources();
        mSmallIconSize = res.getDimensionPixelSize(R.dimen.abc_slice_icon_size);
        mLargeIconSize = res.getDimensionPixelSize(R.dimen.abc_slice_shortcut_size);
    }

    @Override
    public void setSlice(Slice slice) {
        removeAllViews();
        determineShortcutItems(getContext(), slice);
        SliceItem colorItem = SliceQuery.find(slice, SliceItem.TYPE_COLOR);
        if (colorItem == null) {
            colorItem = SliceQuery.find(slice, SliceItem.TYPE_COLOR);
        }
        // TODO: pick better default colour
        final int color = colorItem != null ? colorItem.getColor() : Color.GRAY;
        ShapeDrawable circle = new ShapeDrawable(new OvalShape());
        circle.setTint(color);
        setBackground(circle);
        if (mIcon != null) {
            final boolean isLarge = mIcon.hasHint(Slice.HINT_LARGE)
                    || mIcon.hasHint(Slice.HINT_SOURCE);
            final int iconSize = isLarge ? mLargeIconSize : mSmallIconSize;
            SliceViewUtil.createCircledIcon(getContext(), color, iconSize, mIcon.getIcon(),
                    isLarge, this /* parent */);
            mUri = slice.getUri();
            setClickable(true);
        } else {
            setClickable(false);
        }
    }

    @Override
    public @SliceView.SliceMode int getMode() {
        return SliceView.MODE_SHORTCUT;
    }

    @Override
    public boolean performClick() {
        if (!callOnClick()) {
            try {
                if (mAction != null) {
                    mAction.send();
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(mUri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
            } catch (CanceledException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * Looks at the slice and determines which items are best to use to compose the shortcut.
     */
    private void determineShortcutItems(Context context, Slice slice) {
        SliceItem titleItem = SliceQuery.find(slice, SliceItem.TYPE_ACTION,
                Slice.HINT_TITLE, null);

        if (titleItem != null) {
            // Preferred case: hinted action containing hinted image and text
            mAction = titleItem.getAction();
            mIcon = SliceQuery.find(titleItem.getSlice(), SliceItem.TYPE_IMAGE, Slice.HINT_TITLE,
                    null);
            mLabel = SliceQuery.find(titleItem.getSlice(), SliceItem.TYPE_TEXT, Slice.HINT_TITLE,
                    null);
        } else {
            // No hinted action; just use the first one
            SliceItem actionItem = SliceQuery.find(slice, SliceItem.TYPE_ACTION, (String) null,
                    null);
            mAction = (actionItem != null) ? actionItem.getAction() : null;
        }
        // First fallback: any hinted image and text
        if (mIcon == null) {
            mIcon = SliceQuery.find(slice, SliceItem.TYPE_IMAGE, Slice.HINT_TITLE,
                    null);
        }
        if (mLabel == null) {
            mLabel = SliceQuery.find(slice, SliceItem.TYPE_TEXT, Slice.HINT_TITLE,
                    null);
        }
        // Second fallback: first image and text
        if (mIcon == null) {
            mIcon = SliceQuery.find(slice, SliceItem.TYPE_IMAGE, (String) null,
                    null);
        }
        if (mLabel == null) {
            mLabel = SliceQuery.find(slice, SliceItem.TYPE_TEXT, (String) null,
                    null);
        }
        // Final fallback: use app info
        if (mIcon == null || mLabel == null || mAction == null) {
            PackageManager pm = context.getPackageManager();
            ProviderInfo providerInfo = pm.resolveContentProvider(
                    slice.getUri().getAuthority(), 0);
            ApplicationInfo appInfo = providerInfo.applicationInfo;
            if (appInfo != null) {
                if (mIcon == null) {
                    Slice.Builder sb = new Slice.Builder(slice.getUri());
                    Drawable icon = pm.getApplicationIcon(appInfo);
                    sb.addIcon(SliceViewUtil.createIconFromDrawable(icon), Slice.HINT_LARGE);
                    mIcon = sb.build().getItems().get(0);
                }
                if (mLabel == null) {
                    Slice.Builder sb = new Slice.Builder(slice.getUri());
                    sb.addText(pm.getApplicationLabel(appInfo));
                    mLabel = sb.build().getItems().get(0);
                }
                if (mAction == null) {
                    mAction = PendingIntent.getActivity(context, 0,
                            pm.getLaunchIntentForPackage(appInfo.packageName), 0);
                }
            }
        }
    }
}
