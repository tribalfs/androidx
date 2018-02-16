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

import static android.app.slice.Slice.HINT_ACTIONS;
import static android.app.slice.Slice.HINT_SHORTCUT;
import static android.app.slice.Slice.HINT_TITLE;
import static android.app.slice.Slice.SUBTYPE_COLOR;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_INT;
import static android.app.slice.SliceItem.FORMAT_SLICE;
import static android.app.slice.SliceItem.FORMAT_TEXT;
import static android.app.slice.SliceItem.FORMAT_TIMESTAMP;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;

import androidx.app.slice.SliceItem;
import androidx.app.slice.core.SliceQuery;

/**
 * Extracts information required to present content in a grid format from a slice.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class GridContent {

    private boolean mAllImages;
    private SliceItem mColorItem;
    private SliceItem mPrimaryAction;
    private ArrayList<CellContent> mGridContent = new ArrayList<>();
    private int mMaxCellLineCount;
    private boolean mHasImage;

    public GridContent(SliceItem gridItem) {
        populate(gridItem);
    }

    private void reset() {
        mColorItem = null;
        mMaxCellLineCount = 0;
        mHasImage = false;
        mGridContent.clear();
    }

    /**
     * @return whether this grid has content that is valid to display.
     */
    public boolean populate(SliceItem gridItem) {
        reset();
        mColorItem = SliceQuery.findSubtype(gridItem, FORMAT_INT, SUBTYPE_COLOR);
        String[] hints = new String[] {HINT_SHORTCUT, HINT_TITLE};
        mPrimaryAction = SliceQuery.find(gridItem, FORMAT_SLICE, hints,
                new String[] {HINT_ACTIONS} /* nonHints */);
        mAllImages = true;
        if (FORMAT_SLICE.equals(gridItem.getFormat())) {
            List<SliceItem> items = gridItem.getSlice().getItems();
            items = filterInvalidItems(items);
            // Check if it it's only one item that is a slice
            if (items.size() == 1 && items.get(0).getFormat().equals(FORMAT_SLICE)) {
                items = items.get(0).getSlice().getItems();
            }
            for (int i = 0; i < items.size(); i++) {
                SliceItem item = items.get(i);
                CellContent cc = new CellContent(item);
                processContent(cc);
            }
        } else {
            CellContent cc = new CellContent(gridItem);
            processContent(cc);
        }
        return isValid();
    }

    private void processContent(CellContent cc) {
        if (cc.isValid()) {
            mGridContent.add(cc);
            if (!cc.isImageOnly()) {
                mAllImages = false;
            }
            mMaxCellLineCount = Math.max(mMaxCellLineCount, cc.getTextCount());
            mHasImage |= cc.hasImage();
        }
    }

    /**
     * @return the list of cell content for this grid.
     */
    @NonNull
    public ArrayList<CellContent> getGridContent() {
        return mGridContent;
    }

    /**
     * @return the color to tint content in this grid.
     */
    @Nullable
    public SliceItem getColorItem() {
        return mColorItem;
    }

    /**
     * @return the content intent item for this grid.
     */
    @Nullable
    public SliceItem getContentIntent() {
        return mPrimaryAction;
    }

    /**
     * @return whether this grid has content that is valid to display.
     */
    public boolean isValid() {
        return mGridContent.size() > 0;
    }

    /**
     * @return whether the contents of this grid is just images.
     */
    public boolean isAllImages() {
        return mAllImages;
    }

    private List<SliceItem> filterInvalidItems(List<SliceItem> items) {
        List<SliceItem> filteredItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SliceItem item = items.get(i);
            if (!item.hasHint(HINT_SHORTCUT)) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    /**
     * @return the max number of lines of text in the cells of this grid row.
     */
    public int getMaxCellLineCount() {
        return mMaxCellLineCount;
    }

    /**
     * @return whether this row contains an image.
     */
    public boolean hasImage() {
        return mHasImage;
    }

    /**
     * Extracts information required to present content in a cell.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static class CellContent {
        private SliceItem mContentIntent;
        private ArrayList<SliceItem> mCellItems = new ArrayList<>();
        private int mTextCount;
        private boolean mHasImage;

        public CellContent(SliceItem cellItem) {
            populate(cellItem);
        }

        /**
         * @return whether this row has content that is valid to display.
         */
        public boolean populate(SliceItem cellItem) {
            final String format = cellItem.getFormat();
            if (!cellItem.hasHint(HINT_SHORTCUT)
                    && (FORMAT_SLICE.equals(format) || FORMAT_ACTION.equals(format))) {
                List<SliceItem> items = cellItem.getSlice().getItems();
                // If we've only got one item that's a slice / action use those items instead
                if (items.size() == 1 && (FORMAT_ACTION.equals(items.get(0).getFormat())
                        || FORMAT_SLICE.equals(items.get(0).getFormat()))) {
                    mContentIntent = items.get(0);
                    items = items.get(0).getSlice().getItems();
                }
                if (FORMAT_ACTION.equals(format)) {
                    mContentIntent = cellItem;
                }
                mTextCount = 0;
                int imageCount = 0;
                for (int i = 0; i < items.size(); i++) {
                    final SliceItem item = items.get(i);
                    final String itemFormat = item.getFormat();
                    if (mTextCount < 2 && (FORMAT_TEXT.equals(itemFormat)
                            || FORMAT_TIMESTAMP.equals(itemFormat))) {
                        mTextCount++;
                        mCellItems.add(item);
                    } else if (imageCount < 1 && FORMAT_IMAGE.equals(item.getFormat())) {
                        imageCount++;
                        mHasImage = true;
                        mCellItems.add(item);
                    }
                }
            } else if (isValidCellContent(cellItem)) {
                mCellItems.add(cellItem);
            }
            return isValid();
        }

        /**
         * @return the action to activate when this cell is tapped.
         */
        public SliceItem getContentIntent() {
            return mContentIntent;
        }

        /**
         * @return the slice items to display in this cell.
         */
        public ArrayList<SliceItem> getCellItems() {
            return mCellItems;
        }

        /**
         * @return whether this is content that is valid to show in a grid cell.
         */
        private boolean isValidCellContent(SliceItem cellItem) {
            final String format = cellItem.getFormat();
            return FORMAT_TEXT.equals(format)
                    || FORMAT_TIMESTAMP.equals(format)
                    || FORMAT_IMAGE.equals(format);
        }

        /**
         * @return whether this grid has content that is valid to display.
         */
        public boolean isValid() {
            return mCellItems.size() > 0 && mCellItems.size() <= 3;
        }

        /**
         * @return whether this cell contains just an image.
         */
        public boolean isImageOnly() {
            return mCellItems.size() == 1 && FORMAT_IMAGE.equals(mCellItems.get(0).getFormat());
        }

        /**
         * @return number of text items in this cell.
         */
        public int getTextCount() {
            return mTextCount;
        }

        /**
         * @return whether this cell contains an image.
         */
        public boolean hasImage() {
            return mHasImage;
        }
    }
}
