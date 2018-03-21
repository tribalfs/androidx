/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.slice.builders.impl;

import static android.app.slice.Slice.HINT_ACTIONS;
import static android.app.slice.Slice.HINT_LARGE;
import static android.app.slice.Slice.HINT_LIST_ITEM;
import static android.app.slice.Slice.HINT_NO_TINT;
import static android.app.slice.Slice.HINT_PARTIAL;
import static android.app.slice.Slice.HINT_SEE_MORE;
import static android.app.slice.Slice.HINT_SHORTCUT;
import static android.app.slice.Slice.HINT_SUMMARY;
import static android.app.slice.Slice.HINT_TITLE;
import static android.app.slice.Slice.SUBTYPE_COLOR;
import static android.app.slice.Slice.SUBTYPE_CONTENT_DESCRIPTION;
import static android.app.slice.SliceItem.FORMAT_TEXT;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.slice.builders.ListBuilder.ICON_IMAGE;
import static androidx.slice.builders.ListBuilder.LARGE_IMAGE;
import static androidx.slice.core.SliceHints.HINT_KEY_WORDS;
import static androidx.slice.core.SliceHints.SUBTYPE_MAX;
import static androidx.slice.core.SliceHints.SUBTYPE_RANGE;
import static androidx.slice.core.SliceHints.SUBTYPE_VALUE;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.net.Uri;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.SliceSpec;
import androidx.slice.builders.SliceAction;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
@RestrictTo(LIBRARY)
public class ListBuilderV1Impl extends TemplateBuilderImpl implements ListBuilder {

    private List<Slice> mSliceActions;
    private Slice mSliceHeader;

    /**
     */
    public ListBuilderV1Impl(Slice.Builder b, SliceSpec spec) {
        super(b, spec);
    }

    /**
     */
    @Override
    public void apply(Slice.Builder builder) {
        if (mSliceHeader != null) {
            builder.addSubSlice(mSliceHeader);
        }
        if (mSliceActions != null) {
            Slice.Builder sb = new Slice.Builder(builder);
            for (int i = 0; i < mSliceActions.size(); i++) {
                sb.addSubSlice(mSliceActions.get(i));
            }
            builder.addSubSlice(sb.addHints(HINT_ACTIONS).build());
        }
    }

    /**
     * Add a row to list builder.
     */
    @NonNull
    @Override
    public void addRow(@NonNull TemplateBuilderImpl builder) {
        getBuilder().addSubSlice(builder.build());
    }

    /**
     */
    @NonNull
    @Override
    public void addGridRow(@NonNull TemplateBuilderImpl builder) {
        getBuilder().addSubSlice(builder.build());
    }

    /**
     */
    @Override
    public void setHeader(@NonNull TemplateBuilderImpl builder) {
        mSliceHeader = builder.build();
    }

    /**
     */
    @Override
    public void addAction(@NonNull SliceAction action) {
        if (mSliceActions == null) {
            mSliceActions = new ArrayList<>();
        }
        Slice.Builder b = new Slice.Builder(getBuilder()).addHints(HINT_ACTIONS);
        mSliceActions.add(action.buildSlice(b));
    }

    @Override
    public void addInputRange(TemplateBuilderImpl builder) {
        getBuilder().addSubSlice(builder.build(), SUBTYPE_RANGE);
    }

    @Override
    public void addRange(TemplateBuilderImpl builder) {
        getBuilder().addSubSlice(builder.build(), SUBTYPE_RANGE);
    }

    /**
     */
    @Override
    public void addSeeMoreRow(TemplateBuilderImpl builder) {
        builder.getBuilder().addHints(HINT_SEE_MORE);
        getBuilder().addSubSlice(builder.build());
    }

    /**
     */
    @Override
    public void addSeeMoreAction(PendingIntent intent) {
        getBuilder().addSubSlice(
                new Slice.Builder(getBuilder())
                        .addHints(HINT_SEE_MORE)
                        .addAction(intent, new Slice.Builder(getBuilder())
                                .addHints(HINT_SEE_MORE).build(), null)
                        .build());
    }


    /**
     * Builder to construct an input row.
     */
    public static class RangeBuilderImpl extends TemplateBuilderImpl implements RangeBuilder {
        private int mMax = 100;
        private int mValue = 0;
        private CharSequence mTitle;
        private CharSequence mContentDescr;

        public RangeBuilderImpl(Slice.Builder sb) {
            super(sb, null);
        }

        @Override
        public void setMax(int max) {
            mMax = max;
        }

        @Override
        public void setValue(int value) {
            mValue = value;
        }

        @Override
        public void setTitle(@NonNull CharSequence title) {
            mTitle = title;
        }

        @Override
        public void setContentDescription(@NonNull CharSequence description) {
            mContentDescr = description;
        }

        @Override
        public void apply(Slice.Builder builder) {
            if (mTitle != null) {
                builder.addText(mTitle, null, HINT_TITLE);
            }
            if (mContentDescr != null) {
                builder.addText(mContentDescr, SUBTYPE_CONTENT_DESCRIPTION);
            }
            builder.addHints(HINT_LIST_ITEM)
                    .addInt(mMax, SUBTYPE_MAX)
                    .addInt(mValue, SUBTYPE_VALUE);
        }
    }

    /**
     * Builder to construct an input range row.
     */
    public static class InputRangeBuilderImpl
            extends RangeBuilderImpl implements InputRangeBuilder {
        private PendingIntent mAction;
        private Icon mThumb;

        public InputRangeBuilderImpl(Slice.Builder sb) {
            super(sb);
        }

        @Override
        public void setAction(@NonNull PendingIntent action) {
            mAction = action;
        }

        @Override
        public void setThumb(@NonNull Icon thumb) {
            mThumb = thumb;
        }

        @Override
        public void apply(Slice.Builder builder) {
            if (mAction == null) {
                throw new IllegalStateException("Input ranges must have an associated action.");
            }
            Slice.Builder sb = new Slice.Builder(builder);
            super.apply(sb);
            if (mThumb != null) {
                sb.addIcon(mThumb, null);
            }
            builder.addAction(mAction, sb.build(), SUBTYPE_RANGE).addHints(HINT_LIST_ITEM);
        }
    }

    /**
     */
    @NonNull
    @Override
    public void setColor(@ColorInt int color) {
        getBuilder().addInt(color, SUBTYPE_COLOR);
    }

    /**
     */
    @Override
    public void setKeywords(@NonNull List<String> keywords) {
        Slice.Builder sb = new Slice.Builder(getBuilder());
        for (int i = 0; i < keywords.size(); i++) {
            sb.addText(keywords.get(i), null);
        }
        getBuilder().addSubSlice(sb.addHints(HINT_KEY_WORDS).build());
    }

    /**
     */
    @Override
    public TemplateBuilderImpl createRowBuilder() {
        return new RowBuilderImpl(this);
    }

    /**
     */
    @Override
    public TemplateBuilderImpl createRowBuilder(Uri uri) {
        return new RowBuilderImpl(uri);
    }


    @Override
    public TemplateBuilderImpl createInputRangeBuilder() {
        return new InputRangeBuilderImpl(createChildBuilder());
    }

    @Override
    public TemplateBuilderImpl createRangeBuilder() {
        return new RangeBuilderImpl(createChildBuilder());
    }

    /**
     */
    @Override
    public TemplateBuilderImpl createGridBuilder() {
        return new GridRowBuilderListV1Impl(this);
    }

    /**
     */
    @Override
    public TemplateBuilderImpl createHeaderBuilder() {
        return new HeaderBuilderImpl(this);
    }

    @Override
    public TemplateBuilderImpl createHeaderBuilder(Uri uri) {
        return new HeaderBuilderImpl(uri);
    }

    /**
     */
    public static class RowBuilderImpl extends TemplateBuilderImpl
            implements ListBuilder.RowBuilder {

        private SliceAction mPrimaryAction;
        private SliceItem mTitleItem;
        private SliceItem mSubtitleItem;
        private Slice mStartItem;
        private ArrayList<Slice> mEndItems = new ArrayList<>();
        private CharSequence mContentDescr;

        /**
         */
        public RowBuilderImpl(@NonNull ListBuilderV1Impl parent) {
            super(parent.createChildBuilder(), null);
        }

        /**
         */
        public RowBuilderImpl(@NonNull Uri uri) {
            super(new Slice.Builder(uri), null);
        }

        /**
         */
        public RowBuilderImpl(Slice.Builder builder) {
            super(builder, null);
        }

        /**
         */
        @NonNull
        @Override
        public void setTitleItem(long timeStamp) {
            mStartItem = new Slice.Builder(getBuilder())
                    .addTimestamp(timeStamp, null).addHints(HINT_TITLE).build();
        }

        /**
         */
        @NonNull
        @Override
        public void setTitleItem(Icon icon, int imageMode) {
            setTitleItem(icon, imageMode, false /* isLoading */);
        }

        /**
         */
        @NonNull
        @Override
        public void setTitleItem(Icon icon, int imageMode, boolean isLoading) {
            ArrayList<String> hints = new ArrayList<>();
            if (imageMode != ICON_IMAGE) {
                hints.add(HINT_NO_TINT);
            }
            if (imageMode == LARGE_IMAGE) {
                hints.add(HINT_LARGE);
            }
            if (isLoading) {
                hints.add(HINT_PARTIAL);
            }
            Slice.Builder sb = new Slice.Builder(getBuilder())
                    .addIcon(icon, null /* subType */, hints);
            if (isLoading) {
                sb.addHints(HINT_PARTIAL);
            }
            mStartItem = sb.addHints(HINT_TITLE).build();
        }

        /**
         */
        @NonNull
        @Override
        public void setTitleItem(@NonNull SliceAction action) {
            setTitleItem(action, false /* isLoading */);
        }

        /**
         */
        @Override
        public void setTitleItem(SliceAction action, boolean isLoading) {
            Slice.Builder sb = new Slice.Builder(getBuilder()).addHints(HINT_TITLE);
            if (isLoading) {
                sb.addHints(HINT_PARTIAL);
            }
            mStartItem = action.buildSlice(sb);
        }

        /**
         */
        @NonNull
        @Override
        public void setPrimaryAction(@NonNull SliceAction action) {
            mPrimaryAction = action;
        }

        /**
         */
        @NonNull
        @Override
        public void setTitle(CharSequence title) {
            setTitle(title, false /* isLoading */);
        }

        /**
         */
        @Override
        public void setTitle(CharSequence title, boolean isLoading) {
            mTitleItem = new SliceItem(title, FORMAT_TEXT, null, new String[] {HINT_TITLE});
            if (isLoading) {
                mTitleItem.addHint(HINT_PARTIAL);
            }
        }

        /**
         */
        @NonNull
        @Override
        public void setSubtitle(CharSequence subtitle) {
            setSubtitle(subtitle, false /* isLoading */);
        }

        /**
         */
        @Override
        public void setSubtitle(CharSequence subtitle, boolean isLoading) {
            mSubtitleItem = new SliceItem(subtitle, FORMAT_TEXT, null, new String[0]);
            if (isLoading) {
                mSubtitleItem.addHint(HINT_PARTIAL);
            }
        }

        /**
         */
        @NonNull
        @Override
        public void addEndItem(long timeStamp) {
            mEndItems.add(new Slice.Builder(getBuilder()).addTimestamp(timeStamp,
                    null, new String[0]).build());
        }

        /**
         */
        @NonNull
        @Override
        public void addEndItem(Icon icon, int imageMode) {
            addEndItem(icon, imageMode, false /* isLoading */);
        }

        /**
         */
        @NonNull
        @Override
        public void addEndItem(Icon icon, int imageMode, boolean isLoading) {
            ArrayList<String> hints = new ArrayList<>();
            if (imageMode != ICON_IMAGE) {
                hints.add(HINT_NO_TINT);
            }
            if (imageMode == LARGE_IMAGE) {
                hints.add(HINT_LARGE);
            }
            if (isLoading) {
                hints.add(HINT_PARTIAL);
            }
            Slice.Builder sb = new Slice.Builder(getBuilder())
                    .addIcon(icon, null /* subType */, hints);
            if (isLoading) {
                sb.addHints(HINT_PARTIAL);
            }
            mEndItems.add(sb.build());
        }

        /**
         */
        @NonNull
        @Override
        public void addEndItem(@NonNull SliceAction action) {
            addEndItem(action, false /* isLoading */);
        }

        /**
         */
        @Override
        public void addEndItem(@NonNull SliceAction action, boolean isLoading) {
            Slice.Builder sb = new Slice.Builder(getBuilder());
            if (isLoading) {
                sb.addHints(HINT_PARTIAL);
            }
            mEndItems.add(action.buildSlice(sb));
        }

        @Override
        public void setContentDescription(CharSequence description) {
            mContentDescr = description;
        }

        /**
         */
        @Override
        public void apply(Slice.Builder b) {
            if (mStartItem != null) {
                b.addSubSlice(mStartItem);
            }
            if (mTitleItem != null) {
                b.addItem(mTitleItem);
            }
            if (mSubtitleItem != null) {
                b.addItem(mSubtitleItem);
            }
            for (int i = 0; i < mEndItems.size(); i++) {
                Slice item = mEndItems.get(i);
                b.addSubSlice(item);
            }
            if (mContentDescr != null) {
                b.addText(mContentDescr, SUBTYPE_CONTENT_DESCRIPTION);
            }
            if (mPrimaryAction != null) {
                Slice.Builder sb = new Slice.Builder(
                        getBuilder()).addHints(HINT_TITLE, HINT_SHORTCUT);
                b.addSubSlice(mPrimaryAction.buildSlice(sb), null);
            }
            b.addHints(HINT_LIST_ITEM);
        }
    }

    /**
     */
    public static class HeaderBuilderImpl extends TemplateBuilderImpl
            implements ListBuilder.HeaderBuilder {

        private SliceItem mTitleItem;
        private SliceItem mSubtitleItem;
        private SliceItem mSummaryItem;
        private SliceAction mPrimaryAction;
        private CharSequence mContentDescr;

        /**
         */
        public HeaderBuilderImpl(@NonNull ListBuilderV1Impl parent) {
            super(parent.createChildBuilder(), null);
        }

        /**
         */
        public HeaderBuilderImpl(@NonNull Uri uri) {
            super(new Slice.Builder(uri), null);
        }

        /**
         */
        @Override
        public void apply(Slice.Builder b) {
            if (mTitleItem != null) {
                b.addItem(mTitleItem);
            }
            if (mSubtitleItem != null) {
                b.addItem(mSubtitleItem);
            }
            if (mSummaryItem != null) {
                b.addItem(mSummaryItem);
            }
            if (mContentDescr != null) {
                b.addText(mContentDescr, SUBTYPE_CONTENT_DESCRIPTION);
            }
            if (mPrimaryAction != null) {
                Slice.Builder sb = new Slice.Builder(
                        getBuilder()).addHints(HINT_TITLE, HINT_SHORTCUT);
                b.addSubSlice(mPrimaryAction.buildSlice(sb), null /* subtype */);
            }
        }

        /**
         */
        @Override
        public void setTitle(CharSequence title, boolean isLoading) {
            mTitleItem = new SliceItem(title, FORMAT_TEXT, null, new String[] {HINT_TITLE});
            if (isLoading) {
                mTitleItem.addHint(HINT_PARTIAL);
            }
        }

        /**
         */
        @Override
        public void setSubtitle(CharSequence subtitle, boolean isLoading) {
            mSubtitleItem = new SliceItem(subtitle, FORMAT_TEXT, null, new String[0]);
            if (isLoading) {
                mSubtitleItem.addHint(HINT_PARTIAL);
            }
        }

        /**
         */
        @Override
        public void setSummarySubtitle(CharSequence summarySubtitle, boolean isLoading) {
            mSummaryItem = new SliceItem(summarySubtitle, FORMAT_TEXT, null,
                    new String[] {HINT_SUMMARY});
            if (isLoading) {
                mSummaryItem.addHint(HINT_PARTIAL);
            }
        }

        /**
         */
        @Override
        public void setPrimaryAction(SliceAction action) {
            mPrimaryAction = action;
        }

        /**
         */
        @Override
        public void setContentDescription(CharSequence description) {
            mContentDescr = description;
        }
    }
}
