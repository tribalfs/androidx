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

import static android.app.slice.Slice.HINT_HORIZONTAL;
import static android.app.slice.Slice.SUBTYPE_MESSAGE;
import static android.app.slice.Slice.SUBTYPE_SOURCE;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_INT;
import static android.app.slice.SliceItem.FORMAT_TEXT;

import android.annotation.TargetApi;
import android.app.slice.Slice;
import android.content.Context;
import android.support.annotation.RestrictTo;
import android.support.v7.widget.RecyclerView;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import androidx.app.slice.SliceItem;
import androidx.app.slice.core.SliceQuery;
import androidx.app.slice.view.R;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@TargetApi(24)
public class LargeSliceAdapter extends RecyclerView.Adapter<LargeSliceAdapter.SliceViewHolder> {

    static final int TYPE_DEFAULT       = 1;
    static final int TYPE_HEADER        = 2; // TODO: headers shouldn't scroll off
    static final int TYPE_GRID          = 3;
    static final int TYPE_MESSAGE       = 4;
    static final int TYPE_MESSAGE_LOCAL = 5;

    private final IdGenerator mIdGen = new IdGenerator();
    private final Context mContext;
    private List<SliceWrapper> mSlices = new ArrayList<>();

    private SliceView.OnSliceActionListener mSliceObserver;
    private int mColor;
    private AttributeSet mAttrs;

    public LargeSliceAdapter(Context context) {
        mContext = context;
        setHasStableIds(true);
    }

    public void setSliceObserver(SliceView.OnSliceActionListener observer) {
        mSliceObserver = observer;
    }

    /**
     * Set the {@link SliceItem}'s to be displayed in the adapter and the accent color.
     */
    public void setSliceItems(List<SliceItem> slices, int color) {
        if (slices == null) {
            mSlices.clear();
        } else {
            mIdGen.resetUsage();
            mSlices = slices.stream().map(new Function<SliceItem, SliceWrapper>() {
                @Override
                public SliceWrapper apply(SliceItem s) {
                    return new SliceWrapper(s, mIdGen);
                }
            }).collect(Collectors.<SliceWrapper>toList());
        }
        mColor = color;
        notifyDataSetChanged();
    }

    /**
     * Sets the attribute set to use for views in the list.
     */
    public void setStyle(AttributeSet attrs) {
        mAttrs = attrs;
        notifyDataSetChanged();
    }

    @Override
    public SliceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = inflateForType(viewType);
        v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return new SliceViewHolder(v);
    }

    @Override
    public int getItemViewType(int position) {
        return mSlices.get(position).mType;
    }

    @Override
    public long getItemId(int position) {
        return mSlices.get(position).mId;
    }

    @Override
    public int getItemCount() {
        return mSlices.size();
    }

    @Override
    public void onBindViewHolder(SliceViewHolder holder, int position) {
        SliceWrapper slice = mSlices.get(position);
        if (holder.mSliceView != null) {
            holder.mSliceView.setTint(mColor);
            holder.mSliceView.setStyle(mAttrs);
            holder.mSliceView.setSliceItem(slice.mItem, position == 0 /* isHeader */,
                    position, mSliceObserver);
        }
    }

    private View inflateForType(int viewType) {
        switch (viewType) {
            case TYPE_GRID:
                return LayoutInflater.from(mContext).inflate(R.layout.abc_slice_grid, null);
            case TYPE_MESSAGE:
                return LayoutInflater.from(mContext).inflate(R.layout.abc_slice_message, null);
            case TYPE_MESSAGE_LOCAL:
                return LayoutInflater.from(mContext).inflate(R.layout.abc_slice_message_local,
                        null);
        }
        return new RowView(mContext);
    }

    protected static class SliceWrapper {
        private final SliceItem mItem;
        private final int mType;
        private final long mId;

        public SliceWrapper(SliceItem item, IdGenerator idGen) {
            mItem = item;
            mType = getFormat(item);
            mId = idGen.getId(item);
        }

        public static int getFormat(SliceItem item) {
            if (SUBTYPE_MESSAGE.equals(item.getSubType())) {
                // TODO: Better way to determine me or not? Something more like Messaging style.
                if (SliceQuery.findSubtype(item, null, SUBTYPE_SOURCE) != null) {
                    return TYPE_MESSAGE;
                } else {
                    return TYPE_MESSAGE_LOCAL;
                }
            }
            if (item.hasHint(HINT_HORIZONTAL)) {
                return TYPE_GRID;
            }
            if (!item.hasHint(Slice.HINT_LIST_ITEM)) {
                return TYPE_HEADER;
            }
            return TYPE_DEFAULT;
        }
    }

    /**
     * A {@link RecyclerView.ViewHolder} for presenting slices in {@link LargeSliceAdapter}.
     */
    public static class SliceViewHolder extends RecyclerView.ViewHolder {
        public final SliceChildView mSliceView;

        public SliceViewHolder(View itemView) {
            super(itemView);
            mSliceView = itemView instanceof SliceChildView ? (SliceChildView) itemView : null;
        }
    }

    private static class IdGenerator {
        private long mNextLong = 0;
        private final ArrayMap<String, Long> mCurrentIds = new ArrayMap<>();
        private final ArrayMap<String, Integer> mUsedIds = new ArrayMap<>();

        public long getId(SliceItem item) {
            String str = genString(item);
            if (!mCurrentIds.containsKey(str)) {
                mCurrentIds.put(str, mNextLong++);
            }
            long id = mCurrentIds.get(str);
            int index = mUsedIds.getOrDefault(str, 0);
            mUsedIds.put(str, index + 1);
            return id + index * 10000;
        }

        private String genString(SliceItem item) {
            final StringBuilder builder = new StringBuilder();
            SliceQuery.stream(item).forEach(new Consumer<SliceItem>() {
                @Override
                public void accept(SliceItem i) {
                    builder.append(i.getFormat());
                    //i.removeHint(Slice.HINT_SELECTED);
                    builder.append(i.getHints());
                    switch (i.getFormat()) {
                        case FORMAT_IMAGE:
                            builder.append(i.getIcon());
                            break;
                        case FORMAT_TEXT:
                            builder.append(i.getText());
                            break;
                        case FORMAT_INT:
                            builder.append(i.getInt());
                            break;
                    }
                }
            });
            return builder.toString();
        }

        public void resetUsage() {
            mUsedIds.clear();
        }
    }
}
