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

package androidx.slice.widget;

import static android.app.slice.Slice.HINT_LARGE;
import static android.app.slice.Slice.HINT_NO_TINT;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_SLICE;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.view.R;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(19)
public class ShortcutView extends SliceChildView {

    private static final String TAG = "ShortcutView";

    private ListContent mListContent;
    private Uri mUri;
    private SliceItem mActionItem;
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
    public void setSliceContent(ListContent sliceContent) {
        resetView();
        mListContent = sliceContent;
        if (mListContent == null) {
            return;
        }
        ShortcutContent shortcutContent = new ShortcutContent(sliceContent);
        mActionItem = shortcutContent.getActionItem();
        mIcon = shortcutContent.getIcon();
        mLabel = shortcutContent.getLabel();
        if (mIcon == null || mIcon.getIcon() == null || mLabel == null || mActionItem == null) {
            useAppDataAsFallbackItems(getContext());
        }
        SliceItem colorItem = shortcutContent.getColorItem();
        final int color = colorItem != null
                ? colorItem.getInt()
                : SliceViewUtil.getColorAccent(getContext());
        Drawable circle = DrawableCompat.wrap(new ShapeDrawable(new OvalShape()));
        DrawableCompat.setTint(circle, color);
        ImageView iv = new ImageView(getContext());
        if (mIcon != null && !mIcon.hasHint(HINT_NO_TINT)) {
            // Only set the background if we're tintable
            iv.setBackground(circle);
        }
        addView(iv);
        if (mIcon != null) {
            boolean isImage = mIcon.hasHint(HINT_NO_TINT);
            final int iconSize = isImage ? mLargeIconSize : mSmallIconSize;
            SliceViewUtil.createCircledIcon(getContext(), iconSize, mIcon.getIcon(),
                    isImage, this /* parent */);
            mUri = sliceContent.getSlice().getUri();
            setClickable(true);
        } else {
            setClickable(false);
        }

        // Set the parent layout gravity to center in order to align icons.
        LayoutParams lp = (LayoutParams) iv.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        setLayoutParams(lp);
    }

    @Override
    public @SliceView.SliceMode int getMode() {
        return SliceView.MODE_SHORTCUT;
    }

    @Override
    public boolean performClick() {
        if (mListContent == null) {
            return false;
        }
        if (!callOnClick()) {
            try {
                if (mActionItem != null) {
                    mActionItem.fireAction(null, null);
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(mUri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
                if (mObserver != null) {
                    EventInfo ei = new EventInfo(SliceView.MODE_SHORTCUT,
                            EventInfo.ACTION_TYPE_BUTTON,
                            EventInfo.ROW_TYPE_SHORTCUT, 0 /* rowIndex */);
                    SliceItem interactedItem = mActionItem != null
                            ? mActionItem
                            : new SliceItem(mListContent.getSlice(), FORMAT_SLICE,
                                    null /* subtype */, mListContent.getSlice().getHints());
                    mObserver.onSliceAction(ei, interactedItem);
                }
            } catch (CanceledException e) {
                Log.e(TAG, "PendingIntent for slice cannot be sent", e);
            }
        }
        return true;
    }

    /**
     * Uses app data as the last fallback items for shortcut view.
     */
    private void useAppDataAsFallbackItems(Context context) {
        Slice slice = mListContent.getSlice();
        PackageManager pm = context.getPackageManager();
        ProviderInfo providerInfo = pm.resolveContentProvider(
                slice.getUri().getAuthority(), 0);
        ApplicationInfo appInfo = providerInfo.applicationInfo;
        if (appInfo != null) {
            if (mIcon == null || mIcon.getIcon() == null) {
                Slice.Builder sb = new Slice.Builder(slice.getUri());
                Drawable icon = pm.getApplicationIcon(appInfo);
                sb.addIcon(SliceViewUtil.createIconFromDrawable(icon), HINT_LARGE);
                mIcon = sb.build().getItems().get(0);
            }
            if (mLabel == null) {
                Slice.Builder sb = new Slice.Builder(slice.getUri());
                sb.addText(pm.getApplicationLabel(appInfo), null);
                mLabel = sb.build().getItems().get(0);
            }
            if (mActionItem == null) {
                mActionItem = new SliceItem(PendingIntent.getActivity(context, 0,
                        pm.getLaunchIntentForPackage(appInfo.packageName), 0),
                        new Slice.Builder(slice.getUri()).build(), FORMAT_ACTION,
                        null /* subtype */, null);
            }
        }
    }

    @Override
    public void resetView() {
        mListContent = null;
        mUri = null;
        mActionItem = null;
        mLabel = null;
        mIcon = null;
        setBackground(null);
        removeAllViews();
    }
}
