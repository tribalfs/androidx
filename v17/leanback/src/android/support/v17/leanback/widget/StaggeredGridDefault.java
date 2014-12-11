/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.support.v17.leanback.widget;

/**
 * A default implementation of {@link StaggeredGrid}.
 *
 * This implementation tries to fill items in consecutive row order. The next
 * item is always in same row or in the next row.
 */
final class StaggeredGridDefault extends StaggeredGrid {

    @Override
    public void appendItems(int toLimit) {
        int count = mProvider.getCount();
        int itemIndex;
        int rowIndex;
        if (mLocations.size() > 0) {
            itemIndex = getLastIndex() + 1;
            rowIndex = (mLocations.getLast().row + 1) % mNumRows;
        } else {
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            rowIndex = mStartRow != START_DEFAULT ? mStartRow : itemIndex % mNumRows;
        }

    top_loop:
        while (true) {
            // find endmost row edge (.high is biggest, or .low is smallest in reversed flow)
            int edgeRowIndex = mReversedFlow ?
                    (mLocations.size() > 0 ? getMinLowRowIndex() : -1) :
                    (mLocations.size() > 0 ? getMaxHighRowIndex() : -1);
            int edge = mReversedFlow ?
                    (edgeRowIndex != -1 ? mRows[edgeRowIndex].low : Integer.MAX_VALUE) :
                    (edgeRowIndex != -1 ? mRows[edgeRowIndex].high : Integer.MIN_VALUE);
            // fill from current row till last row so that each row will grow longer than
            // the previous highest row.
            for (; rowIndex < mNumRows; rowIndex++) {
                // fill one item to a row
                if (itemIndex == count) {
                    break top_loop;
                }
                appendItemToRow(itemIndex++, rowIndex);
                // fill more item to the row to make sure this row is longer than
                // the previous highest row.
                if (edgeRowIndex == -1) {
                    edgeRowIndex = mReversedFlow ? getMinLowRowIndex() : getMaxHighRowIndex();
                    edge = mReversedFlow ?
                            mRows[edgeRowIndex].low :
                            mRows[edgeRowIndex].high;
                } else  if (rowIndex != edgeRowIndex) {
                    while (mReversedFlow ?
                            mRows[rowIndex].low > edge :
                            mRows[rowIndex].high < edge) {
                        if (itemIndex == count) {
                            break top_loop;
                        }
                        appendItemToRow(itemIndex++, rowIndex);
                    }
                }
            }
            if (mReversedFlow ?
                    mRows[getMaxLowRowIndex()].low <= toLimit :
                    mRows[getMinHighRowIndex()].high >= toLimit) {
                break;
            }
            // start fill from row 0 again
            rowIndex = 0;
        }
    }

    @Override
    public void prependItems(int toLimit) {
        if (mProvider.getCount() <= 0) return;
        int itemIndex;
        int rowIndex;
        if (mLocations.size() > 0) {
            itemIndex = getFirstIndex() - 1;
            rowIndex = mLocations.getFirst().row;
            if (rowIndex == 0) {
                rowIndex = mNumRows - 1;
            } else {
                rowIndex--;
            }
        } else {
            itemIndex = mStartIndex != START_DEFAULT ? mStartIndex : 0;
            rowIndex = mStartRow != START_DEFAULT ? mStartRow : itemIndex % mNumRows;
        }

    top_loop:
        while (true) {
            // find startmost row edge (.low is smallest, or .high is biggest in reversed flow)
            int edgeRowIndex = mReversedFlow ?
                    (mLocations.size() > 0 ? getMaxHighRowIndex() : -1) :
                    (mLocations.size() > 0 ? getMinLowRowIndex() : -1);
            int edge = mReversedFlow ?
                    (edgeRowIndex != -1 ? mRows[edgeRowIndex].high : Integer.MIN_VALUE) :
                    (edgeRowIndex != -1 ? mRows[edgeRowIndex].low : Integer.MAX_VALUE);
            for (; rowIndex >=0 ; rowIndex--) {
                if (itemIndex < 0) {
                    break top_loop;
                }
                prependItemToRow(itemIndex--, rowIndex);
                if (edgeRowIndex == -1) {
                    edgeRowIndex = mReversedFlow ? getMaxHighRowIndex() : getMinLowRowIndex();
                    edge = mReversedFlow ?
                            mRows[edgeRowIndex].high :
                            mRows[edgeRowIndex].low;
                } else if (rowIndex != edgeRowIndex) {
                    while (mReversedFlow ?
                            mRows[rowIndex].high < edge :
                            mRows[rowIndex].low > edge) {
                        if (itemIndex < 0) {
                            break top_loop;
                        }
                        prependItemToRow(itemIndex--, rowIndex);
                    }
                }
            }
            if (mReversedFlow ?
                    mRows[getMinHighRowIndex()].high >= toLimit :
                    mRows[getMaxLowRowIndex()].low <= toLimit) {
                break;
            }
            rowIndex = mNumRows - 1;
        }
    }

    @Override
    public final void stripDownTo(int itemIndex) {
        // because we layout the items in the order that next item is either same row
        // or next row,  so we can easily find the row range by searching items forward and
        // backward until we see the row is 0 or mNumRow - 1
        Location loc = getLocation(itemIndex);
        if (loc == null) {
            return;
        }
        int firstIndex = getFirstIndex();
        int lastIndex = getLastIndex();
        int row = loc.row;

        int endIndex = itemIndex;
        int endRow = row;
        while (endRow < mNumRows - 1 && endIndex < lastIndex) {
            endIndex++;
            endRow = getLocation(endIndex).row;
        }

        int startIndex = itemIndex;
        int startRow = row;
        while (startRow > 0 && startIndex > firstIndex) {
            startIndex--;
            startRow = getLocation(startIndex).row;
        }
        // trim information
        for (int i = firstIndex; i < startIndex; i++) {
            removeFirst();
        }
        for (int i = endIndex; i < lastIndex; i++) {
            removeLast();
        }
    }
}
