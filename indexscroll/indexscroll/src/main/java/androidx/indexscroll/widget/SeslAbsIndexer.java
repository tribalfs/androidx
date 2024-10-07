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

package androidx.indexscroll.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.recyclerview.widget.LinearLayoutManager.INVALID_OFFSET;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.indexscroll.widget.SeslIndexScrollView.IndexScroll;

import java.text.Collator;
import java.util.HashMap;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY)
public abstract class SeslAbsIndexer extends DataSetObserver {
    private final static String TAG = "SeslAbsIndexer";

    private final static boolean DEBUG = false;

    private static final char DIGIT_CHAR = '#';
    private static final char FAVORITE_CHAR = '★';
    private static final String GROUP_CHAR = "\ud83d\udc65︎";
    private static final char GROUP_CHECKER = '\ud83d';
    private static final char SYMBOL_BASE_CHAR = '!';
    private static final char SYMBOL_CHAR = '&';

    static final String INDEXSCROLL_INDEX_COUNTS = "indexscroll_index_counts";
    static final String INDEXSCROLL_INDEX_TITLES = "indexscroll_index_titles";

    private SparseIntArray mAlphaMap;
    private CharSequence mAlphabet;
    private String[] mAlphabetArray;
    private Bundle mBundle;
    protected Collator mCollator;
    private final DataSetObservable mDataSetObservable = new DataSetObservable();
    private String[] mLangAlphabetArray;
    //private HashMap<Integer, Integer> mLangIndexMap = new HashMap<>();

    private int mAlphabetLength;
    private int[] mCachingValue;
    private int mFavoriteItemCount = 0;
    private int mProfileItemCount = 0;
    private int mGroupItemCount = 0;
    private int mDigitItemCount = 0;

    private boolean mUseFavoriteIndex = false;
    private boolean mRegisteredDataSetObservable = false;
    private boolean mUseGroupIndex = false;
    private boolean mUseDigitIndex = false;
    private boolean mIsInitialized = false;

    protected abstract Bundle getBundle();

    protected abstract String getItemAt(int pos);

    protected abstract int getItemCount();

    protected abstract boolean isDataToBeIndexedAvailable();

    SeslAbsIndexer(CharSequence indexCharacters) {
        mUseFavoriteIndex = false;
        mProfileItemCount = 0;
        mFavoriteItemCount = 0;
        initIndexer(indexCharacters);
    }

    SeslAbsIndexer(String[] indexCharacters, int aLangIndex) {
        mUseFavoriteIndex = false;
        mProfileItemCount = 0;
        mFavoriteItemCount = 0;
        mLangAlphabetArray = indexCharacters;
        setIndexerArray();
    }

    String[] getLangAlphabetArray() {
        return mLangAlphabetArray;
    }

    int getCachingValue(int index) {
        if (index >= 0 && index < mAlphabetLength) {
            return mCachingValue[index];
        }
        return -1;
    }

    int getIndexByPosition(int position) {
        if (mCachingValue == null) {
            return IndexScroll.NO_SELECTED_INDEX;
        }

        int lastIndex = IndexScroll.NO_SELECTED_INDEX;
        for (int i = 0; i < mAlphabetLength; i++) {
            lastIndex = i;

            if (mCachingValue[i] == position) {
                return i;
            }

            if (mCachingValue[i] > position) {
                return i - 1;
            }
        }

        return lastIndex;
    }

    private void setIndexerArray() {
        StringBuilder indexerString = new StringBuilder();

        if (mUseFavoriteIndex) {
            indexerString.append(FAVORITE_CHAR);
        }

        if (mUseGroupIndex) {
            indexerString.append(GROUP_CHECKER);
        }

        int langIndex = 0;
        while (langIndex < mLangAlphabetArray.length) {
            for (int i = 0; i < mLangAlphabetArray[langIndex].length(); i++) {
                //mLangIndexMap.put(indexerString.length(), langIndex);
                indexerString.append(mLangAlphabetArray[langIndex].charAt(i));
            }
            langIndex++;
        }

        if (mUseDigitIndex) {
            //mLangIndexMap.put(indexerString.length(), langIndex - 1);
            indexerString.append(DIGIT_CHAR);
        }

        if (indexerString.length() != 0) {
            initIndexer(indexerString.toString());
        } else {
            Log.w("SeslAbsIndexer",
                    "The array received from App is empty. " +
                    "Indexer must be initialized through additional API.");
        }
    }

    void setProfileItem(int count) {
        if (count >= 0) {
            mProfileItemCount = count;
        }
    }

    void setFavoriteItem(int count) {
        if (count > 0) {
            mFavoriteItemCount = count;
            mUseFavoriteIndex = true;
            setIndexerArray();
        }
    }

    void setGroupItem(int count) {
        if (count > 0) {
            mGroupItemCount = count;
            mUseGroupIndex = true;
            setIndexerArray();
        }
    }

    void setDigitItem(int count) {
        if (count > 0) {
            mDigitItemCount = count;
            mUseDigitIndex = true;
            setIndexerArray();
        }
    }

    private void initIndexer(@NonNull CharSequence alphabet) {
        if (alphabet.length() == 0) {
            throw new IllegalArgumentException("Invalid indexString :"
                    + ((Object) alphabet));
        }

        mAlphabet = alphabet;
        mAlphabetLength = alphabet.length();
        mCachingValue = new int[mAlphabetLength];
        mAlphabetArray = new String[mAlphabetLength];

        for (int i = 0; i < mAlphabetLength; i++) {
            if (mUseGroupIndex && mAlphabet.charAt(i) == GROUP_CHECKER) {
                mAlphabetArray[i] = GROUP_CHAR;
            } else {
                mAlphabetArray[i] = Character.toString(mAlphabet.charAt(i));
            }
        }

        mAlphaMap = new SparseIntArray(mAlphabetLength);

        mCollator = Collator.getInstance();
        mCollator.setStrength(Collator.PRIMARY);

        mIsInitialized = true;
    }

    boolean isInitialized() {
        return mIsInitialized;
    }

    String[] getAlphabetArray() {
        return mAlphabetArray;
    }

    private int compare(String word, String indexString) {
        return mCollator.compare(word, indexString);
    }

    void cacheIndexInfo() {
        if (isDataToBeIndexedAvailable() && getItemCount() != 0) {
            mBundle = getBundle();

            if (mBundle != null) {
                if (mBundle.containsKey(INDEXSCROLL_INDEX_TITLES)
                        && mBundle.containsKey(INDEXSCROLL_INDEX_COUNTS)) {
                    getBundleInfo();
                    return;
                }
            }

            onBeginTransaction();
            for (int i = 0; i < mAlphabetLength; i++) {
                mCachingValue[i] = getPositionForString("" + mAlphabet.charAt(i));
            }
            onEndTransaction();
        }
    }

    private int getPositionForString(String searchString) {

        if (mAlphabet == null) return 0;

        int totalItems = getItemCount();
        if (totalItems == 0 || searchString == null || searchString.isEmpty()) return totalItems ;

        SparseIntArray cacheAlphaMap = mAlphaMap;

        char indexChar = searchString.charAt(0);
        int cachedOffset = cacheAlphaMap.get(indexChar, INVALID_OFFSET);

        int calculatedOffset;

        if (cachedOffset != INVALID_OFFSET) {
            cachedOffset = Math.abs(cachedOffset);
            calculatedOffset = totalItems ;

        } else {
            int offsetFromAlphabets;
            findOffsetInAlphabets: {
                calculatedOffset = mAlphabet.toString().indexOf(indexChar);
                if (calculatedOffset > 0) {
                    offsetFromAlphabets = calculatedOffset - 1;
                    if (indexChar > mAlphabet.charAt(offsetFromAlphabets)) {
                        offsetFromAlphabets = cacheAlphaMap.get(mAlphabet.charAt(offsetFromAlphabets), INVALID_OFFSET);
                        if (offsetFromAlphabets != INVALID_OFFSET) {
                            offsetFromAlphabets = Math.abs(offsetFromAlphabets);
                            break findOffsetInAlphabets;
                        }
                    }
                }
                offsetFromAlphabets = 0;
            }

            cachedOffset = offsetFromAlphabets;

            adjustOffset: {
                if (calculatedOffset <  mAlphabet.length() - 1) {
                    ++calculatedOffset;
                    if (indexChar < mAlphabet.charAt(calculatedOffset)) {
                        calculatedOffset = cacheAlphaMap.get(this.mAlphabet.charAt(calculatedOffset), INVALID_OFFSET);
                        if (calculatedOffset != INVALID_OFFSET) {
                            calculatedOffset = Math.abs(calculatedOffset);
                            break adjustOffset;
                        }
                    }
                }
                calculatedOffset = totalItems ;
            }
        }

        String searchKey;
        if (indexChar == SYMBOL_CHAR) {
            searchKey = "!";
        } else {
            searchKey = searchString;
        }

        int sectionEnd;
        if (indexChar == 9733) {
            sectionEnd = Math.max(cachedOffset, mProfileItemCount);
        } else {
            if (indexChar == GROUP_CHECKER) {
                sectionEnd = Math.max(cachedOffset, mProfileItemCount + mFavoriteItemCount);
            } else {
                sectionEnd = Math.max(cachedOffset, mProfileItemCount + mFavoriteItemCount + mGroupItemCount);
            }
        }

        calculatedOffset -= mDigitItemCount;

        int searchEnd = (indexChar == DIGIT_CHAR) ? calculatedOffset : sectionEnd;

        int middleIndex = (calculatedOffset + searchEnd) / 2;

        int finalPosition = totalItems;
        findFinalPosition: {
            while(middleIndex >= searchEnd && middleIndex < calculatedOffset) {
                String item = getItemAt(middleIndex);
                int areEquivalent;
                if (item != null && !item.isEmpty()) {
                    if (indexChar == 9733 || indexChar == SYMBOL_CHAR || indexChar == GROUP_CHECKER) {
                        areEquivalent = 1;
                    }else{
                        areEquivalent = compare(item, searchKey);
                    }

                    adjustBounds: {
                        if (areEquivalent != 0) {
                            if (areEquivalent < 0) {
                                searchEnd = middleIndex + 1;
                                if (searchEnd >= totalItems ) {
                                    break findFinalPosition;
                                }
                                break adjustBounds;
                            }
                        } else if (searchEnd == middleIndex) {
                            break;
                        }

                        calculatedOffset = middleIndex;
                    }
                    middleIndex = (searchEnd + calculatedOffset) / 2;
                } else {
                    if (middleIndex <= searchEnd) break;
                    --middleIndex;
                }
            }

            finalPosition = middleIndex;
        }

        if (searchString.length() == 1) {
            cacheAlphaMap.put(indexChar, finalPosition);
        }

        return finalPosition;

    }


    private void getBundleInfo() {
        final String[] sections = mBundle.getStringArray(INDEXSCROLL_INDEX_TITLES);
        final int[] counts = mBundle.getIntArray(INDEXSCROLL_INDEX_COUNTS);

        int basePosition = mProfileItemCount;
        int baseSectionIndex = 0;
        for (int index = 0; index < mAlphabetLength; index++) {
            final char targetChar = mAlphabet.charAt(index);
            mCachingValue[index] = basePosition;

            if (DEBUG) {
                Log.d(TAG, "Get index info from bundle (" + index + ") : "
                        + targetChar + " = " + basePosition);
            }

            if (targetChar == FAVORITE_CHAR) {
                basePosition += mFavoriteItemCount;
            } else if (targetChar == GROUP_CHECKER) {
                basePosition += mGroupItemCount;
            }

            int sectionIndex = baseSectionIndex;
            while (true) {
                if (sectionIndex >= sections.length) {
                    break;
                } else if (targetChar == sections[sectionIndex].charAt(0)) {
                    basePosition += counts[sectionIndex];
                    baseSectionIndex = sectionIndex;
                    break;
                } else {
                    sectionIndex++;
                }
            }
        }
    }

    @Override
    public void onChanged() {
        super.onChanged();
        mAlphaMap.clear();
        mDataSetObservable.notifyChanged();
    }

    @Override
    public void onInvalidated() {
        super.onInvalidated();
        mAlphaMap.clear();
        mDataSetObservable.notifyInvalidated();
    }

    void registerDataSetObserver(DataSetObserver observer) {
        if (!mRegisteredDataSetObservable) {
            mRegisteredDataSetObservable = true;
            mDataSetObservable.registerObserver(observer);
        } else {
            Log.e("SeslAbsIndexer", "Observer " + observer + " is already registered.");
        }
    }

    void unregisterDataSetObserver(DataSetObserver observer) {
        if (mRegisteredDataSetObservable) {
            mRegisteredDataSetObservable = false;
            mDataSetObservable.unregisterObserver(observer);
        } else {
            Log.e("SeslAbsIndexer", "Observer " + observer + " was not registered.");
        }
    }

    void onBeginTransaction() {
    }

    void onEndTransaction() {
    }
}
