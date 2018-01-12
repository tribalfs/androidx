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

import static android.app.slice.Slice.HINT_NO_TINT;
import static android.app.slice.Slice.HINT_SELECTED;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_TIMESTAMP;

import static androidx.app.slice.core.SliceHints.EXTRA_TOGGLE_STATE;
import static androidx.app.slice.widget.SliceView.MODE_LARGE;
import static androidx.app.slice.widget.SliceView.MODE_SMALL;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.support.annotation.RestrictTo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;

import androidx.app.slice.Slice;
import androidx.app.slice.SliceItem;
import androidx.app.slice.core.SliceHints;
import androidx.app.slice.core.SliceQuery;
import androidx.app.slice.view.R;

/**
 * Row item is in small template format and can be used to construct list items for use
 * with {@link LargeTemplateView}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@TargetApi(23)
public class RowView extends FrameLayout implements SliceView.SliceModeView,
        LargeSliceAdapter.SliceListView, View.OnClickListener {

    private static final String TAG = "RowView";

    // The number of items that fit on the right hand side of a small slice
    private static final int MAX_END_ITEMS = 3;

    private int mIconSize;
    private int mPadding;
    private boolean mInSmallMode;
    private boolean mIsHeader;

    private LinearLayout mStartContainer;
    private LinearLayout mContent;
    private TextView mPrimaryText;
    private TextView mSecondaryText;
    private View mDivider;
    private CompoundButton mToggle;
    private LinearLayout mEndContainer;

    private int mRowIndex;
    private RowContent mRowContent;
    private SliceItem mColorItem;
    private SliceItem mRowAction;

    private SliceView.SliceObserver mObserver;

    public RowView(Context context) {
        super(context);
        mIconSize = getContext().getResources().getDimensionPixelSize(R.dimen.abc_slice_icon_size);
        mPadding = getContext().getResources().getDimensionPixelSize(R.dimen.abc_slice_padding);
        inflate(context, R.layout.abc_slice_small_template, this);

        mStartContainer = (LinearLayout) findViewById(R.id.icon_frame);
        mContent = (LinearLayout) findViewById(android.R.id.content);
        mPrimaryText = (TextView) findViewById(android.R.id.title);
        mSecondaryText = (TextView) findViewById(android.R.id.summary);
        mDivider = findViewById(R.id.divider);
        mEndContainer = (LinearLayout) findViewById(android.R.id.widget_frame);
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public @SliceView.SliceMode int getMode() {
        return mInSmallMode ? MODE_SMALL : MODE_LARGE;
    }

    @Override
    public void setColor(SliceItem color) {
        mColorItem = color;
    }

    @Override
    public void setSliceObserver(SliceView.SliceObserver observer) {
        mObserver = observer;
    }

    /**
     * This is called when RowView is being used as a component in a large template.
     */
    @Override
    public void setSliceItem(SliceItem slice, boolean isHeader, int index,
            SliceView.SliceObserver observer) {
        setSliceObserver(observer);
        mRowIndex = index;
        mIsHeader = isHeader;
        mInSmallMode = false;
        populateViews(slice);
    }

    /**
     * This is called when RowView is being used as a small template.
     */
    @Override
    public void setSlice(Slice slice) {
        mRowIndex = 0;
        mInSmallMode = true;
        ListContent lc = new ListContent(slice);
        populateViews(lc.getSummaryItem());
    }

    private void populateViews(SliceItem item) {
        resetView();
        final int color = mColorItem != null ? mColorItem.getInt() : -1;
        mRowContent = new RowContent(item, !mIsHeader && !mInSmallMode);

        boolean showStart = false;
        final SliceItem startItem = mRowContent.getStartItem();
        if (startItem != null) {
            final EventInfo info = new EventInfo(getMode(),
                    EventInfo.ACTION_TYPE_BUTTON,
                    EventInfo.ROW_TYPE_LIST, mRowIndex);
            info.setPosition(EventInfo.POSITION_START, 0, 1);
            showStart = addItem(startItem, color, true /* isStart */, 0 /* padding */, info);
        }
        mStartContainer.setVisibility(showStart ? View.VISIBLE : View.GONE);

        final SliceItem titleItem = mRowContent.getTitleItem();
        if (titleItem != null) {
            mPrimaryText.setText(titleItem.getText());
        }
        mPrimaryText.setVisibility(titleItem != null ? View.VISIBLE : View.GONE);

        final SliceItem subTitle = mRowContent.getSubtitleItem();
        if (subTitle != null) {
            mSecondaryText.setText(subTitle.getText());
        }
        mSecondaryText.setVisibility(subTitle != null ? View.VISIBLE : View.GONE);

        mRowAction = mRowContent.getContentIntent();
        SliceItem toggleItem = mRowContent.getToggleItem();
        // Check if content intent + toggle are the same; make whole row clickable
        if (toggleItem != null && toggleItem == mRowAction && addToggle(toggleItem, color)) {
            setViewClickable(this, true);
            // Can't show more end actions if we have a toggle so we're done
            return;
        } else if (toggleItem != null && addToggle(toggleItem, color)) {
            mDivider.setVisibility(mRowAction != null ? View.VISIBLE : View.GONE);
            setViewClickable(mRowAction != null ? mContent : this, true);
            // Can't show more end actions if we have a toggle so we're done
            return;
        }

        // If we're here we might be able to show end items
        ArrayList<SliceItem> endItems = mRowContent.getEndItems();
        if (endItems.size() > 0) {
            int itemCount = 0;
            final String desiredFormat = endItems.get(0).getFormat();
            for (int i = 0; i < endItems.size(); i++) {
                final SliceItem endItem = endItems.get(i);
                if (itemCount <= MAX_END_ITEMS) {
                    final EventInfo info = new EventInfo(getMode(),
                            EventInfo.ACTION_TYPE_BUTTON,
                            EventInfo.ROW_TYPE_LIST, mRowIndex);
                    info.setPosition(EventInfo.POSITION_END, i,
                            Math.min(endItems.size(), MAX_END_ITEMS));
                    if (addItem(endItem, color, false /* isStart */, mPadding, info)) {
                        itemCount++;
                    }
                }
            }
            if (mRowAction != null) {
                if (itemCount > 0 && FORMAT_ACTION.equals(desiredFormat)) {
                    setViewClickable(mContent, true);
                } else {
                    setViewClickable(this, true);
                }
            }
        }
    }

    /**
     * @return Whether a toggle was added.
     */
    private boolean addToggle(final SliceItem toggleItem, int color) {
        if (!FORMAT_ACTION.equals(toggleItem.getFormat())
                || !SliceQuery.hasHints(toggleItem.getSlice(), SliceHints.SUBTYPE_TOGGLE)) {
            return false;
        }

        // Check if this is a custom toggle
        Icon checkedIcon = null;
        List<SliceItem> sliceItems = toggleItem.getSlice().getItems();
        if (sliceItems.size() > 0) {
            checkedIcon = FORMAT_IMAGE.equals(sliceItems.get(0).getFormat())
                    ? sliceItems.get(0).getIcon()
                    : null;
        }
        if (checkedIcon != null) {
            if (color != -1) {
                // TODO - Should these be tinted? What if the app wants diff colors per state?
                checkedIcon.setTint(color);
            }
            mToggle = new ToggleButton(getContext());
            ((ToggleButton) mToggle).setTextOff("");
            ((ToggleButton) mToggle).setTextOn("");
            mToggle.setBackground(checkedIcon.loadDrawable(getContext()));
            mEndContainer.addView(mToggle);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mToggle.getLayoutParams();
            lp.width = mIconSize;
            lp.height = mIconSize;
        } else {
            mToggle = new Switch(getContext());
            mEndContainer.addView(mToggle);
        }
        mToggle.setChecked(SliceQuery.hasHints(toggleItem.getSlice(), HINT_SELECTED));
        mToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    PendingIntent pi = toggleItem.getAction();
                    Intent i = new Intent().putExtra(EXTRA_TOGGLE_STATE, isChecked);
                    pi.send(getContext(), 0, i, null, null);
                    if (mObserver != null) {
                        final EventInfo info = new EventInfo(getMode(),
                                EventInfo.ACTION_TYPE_TOGGLE,
                                EventInfo.ROW_TYPE_LIST, mRowIndex);
                        info.state = isChecked ? EventInfo.STATE_ON : EventInfo.STATE_OFF;
                        mObserver.onSliceAction(info, toggleItem);
                    }
                } catch (CanceledException e) {
                    mToggle.setSelected(!isChecked);
                }
            }
        });
        return true;
    }

    /**
     * Adds simple items to a container. Simple items include actions with icons, images, or
     * timestamps.
     *
     * @return Whether an item was added to the view.
     */
    private boolean addItem(SliceItem sliceItem, int color, boolean isStart, int padding,
            final EventInfo info) {
        SliceItem image = null;
        SliceItem action = null;
        SliceItem timeStamp = null;
        ViewGroup container = isStart ? mStartContainer : mEndContainer;
        if (FORMAT_ACTION.equals(sliceItem.getFormat())
                && !sliceItem.hasHint(SliceHints.SUBTYPE_TOGGLE)) {
            image = SliceQuery.find(sliceItem.getSlice(), FORMAT_IMAGE);
            timeStamp = SliceQuery.find(sliceItem.getSlice(), FORMAT_TIMESTAMP);
            action = sliceItem;
        } else if (FORMAT_IMAGE.equals(sliceItem.getFormat())) {
            image = sliceItem;
        } else if (FORMAT_TIMESTAMP.equals(sliceItem.getFormat())) {
            timeStamp = sliceItem;
        }
        View addedView = null;
        if (image != null) {
            ImageView iv = new ImageView(getContext());
            iv.setImageIcon(image.getIcon());
            if (color != -1 && !sliceItem.hasHint(HINT_NO_TINT)) {
                iv.setColorFilter(color);
            }
            container.addView(iv);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.width = mIconSize;
            lp.height = mIconSize;
            lp.setMarginStart(padding);
            addedView = iv;
        } else if (timeStamp != null) {
            TextView tv = new TextView(getContext());
            tv.setText(SliceViewUtil.getRelativeTimeString(sliceItem.getTimestamp()));
            container.addView(tv);
            addedView = tv;
        }
        if (action != null && addedView != null) {
            final SliceItem sliceAction = action;
            addedView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        sliceAction.getAction().send();
                        if (mObserver != null) {
                            mObserver.onSliceAction(info, sliceAction);
                        }
                    } catch (CanceledException e) {
                        e.printStackTrace();
                    }
                }
            });
            addedView.setBackground(SliceViewUtil.getDrawable(getContext(),
                    android.R.attr.selectableItemBackground));
        }
        return addedView != null;
    }

    @Override
    public void onClick(View view) {
        if (mRowAction != null && FORMAT_ACTION.equals(mRowAction.getFormat())) {
            // Check for a row action
            try {
                mRowAction.getAction().send();
                if (mObserver != null) {
                    EventInfo info = new EventInfo(getMode(), EventInfo.ACTION_TYPE_CONTENT,
                            EventInfo.ROW_TYPE_LIST, mRowIndex);
                    mObserver.onSliceAction(info, mRowAction);
                }
            } catch (CanceledException e) {
                Log.w(TAG, "PendingIntent for slice cannot be sent", e);
            }
        } else if (mToggle != null) {
            // Or no row action so let's just toggle if we've got one
            mToggle.toggle();
        }
    }

    private void setViewClickable(View layout, boolean isClickable) {
        layout.setOnClickListener(isClickable ? this : null);
        layout.setBackground(isClickable ? SliceViewUtil.getDrawable(getContext(),
                android.R.attr.selectableItemBackground) : null);
        layout.setClickable(isClickable);
    }

    @Override
    public void resetView() {
        setViewClickable(this, false);
        setViewClickable(mContent, false);
        mStartContainer.removeAllViews();
        mEndContainer.removeAllViews();
        mPrimaryText.setText(null);
        mSecondaryText.setText(null);
        mToggle = null;
        mRowAction = null;
        mDivider.setVisibility(View.GONE);
    }
}
