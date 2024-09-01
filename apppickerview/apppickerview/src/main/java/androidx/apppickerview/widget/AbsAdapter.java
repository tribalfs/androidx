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

package androidx.apppickerview.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.content.ComponentName;
import android.content.Context;
import android.icu.text.AlphabeticIndex;
import android.os.Build;
import android.os.LocaleList;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.reflect.text.SeslTextUtilsReflector;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

@RestrictTo(LIBRARY_GROUP_PREFIX)
public abstract class AbsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements Filterable, SectionIndexer {
    private static final String TAG = "AppPickerViewAdapter";

    private static final int MAX_OFFSET = 200;

    private final AppPickerIconLoader mAppPickerIconLoader;
    @NonNull
    protected Context mContext;
    private AppPickerView.OnBindListener mOnBindListener;
    AppPickerView.OnSearchFilterListener mOnSearchFilterListener;
    final List<AppPickerView.AppLabelInfo> mDataSet = new ArrayList<>();
    final List<AppPickerView.AppLabelInfo> mDataSetFiltered = new ArrayList<>();
    private final Map<String, Integer> mSectionMap = new HashMap<>();
    private String[] mSections = new String[0];
    String mSearchText = "";

    boolean mHideAllApps = false;

    private final int mForegroundColor;
    private int mOrder;
    private int[] mPositionToSectionIndex;
    protected int mType;

    public AbsAdapter(@NonNull Context context, int type, int order, @Nullable AppPickerIconLoader iconLoader, boolean showDivider) {
        mContext = context;
        mType = type;
        mOrder = order;
        mAppPickerIconLoader = iconLoader;

        TypedValue outValue = new TypedValue();
        mContext.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, outValue, true);
        mForegroundColor = outValue.resourceId != 0 ?
                ContextCompat.getColor(mContext, outValue.resourceId) : outValue.data;
    }

    static AbsAdapter getAppPickerAdapter(Context context, List<String> packageNamesList, int type,
            int order, List<AppPickerView.AppLabelInfo> labelInfoList,
            AppPickerIconLoader iconLoader,
            List<ComponentName> activityNamesList,
             boolean showDivider) {
        final AbsAdapter adapter;
        if (type >= AppPickerView.TYPE_GRID) {
            adapter = new GridAdapter(context, type, order, iconLoader, showDivider);
        } else {
            adapter = new ListAdapter(context, type, order, iconLoader, showDivider);
        }
        adapter.setHasStableIds(true);
        adapter.resetPackages(packageNamesList, false, labelInfoList, activityNamesList);
        return adapter;
    }

    List<AppPickerView.AppLabelInfo> getDataSet() {
        return mDataSet;
    }

    void resetPackages(List<String> packageNamesList,
            boolean dataSetchanged,
            List<AppPickerView.AppLabelInfo> labelInfoList,
            List<ComponentName> activityNamesList) {
        Log.i(TAG, "Start resetpackage dataSetchanged : " + dataSetchanged);

        mDataSet.clear();
        mDataSet.addAll(DataManager.resetPackages(mContext, packageNamesList, labelInfoList, activityNamesList));

        if (Build.VERSION.SDK_INT >= 24) {
            if (getAppLabelComparator(mOrder) != null) {
                mDataSet.sort(getAppLabelComparator(mOrder));
            }
        }

        if (hasAllAppsInList()) {
            if (!mDataSet.isEmpty()) {
                mDataSet.add(0,
                        new AppPickerView.AppLabelInfo(AppPickerView.ALL_APPS_STRING,
                                "", ""));
            }
        }
        mDataSetFiltered.clear();
        mDataSetFiltered.addAll(mDataSet);

        refreshSectionMap();

        if (dataSetchanged) {
            notifyDataSetChanged();
        }

        Log.i(TAG, "End resetpackage");
    }

    void addPackage(int position, String label) {
        mDataSet.add(position,
                new AppPickerView.AppLabelInfo("", label, ""));
        mDataSetFiltered.clear();
        mDataSetFiltered.addAll(mDataSet);

        refreshSectionMap();
        notifyItemInserted(position);
    }

    void addSeparator(int position) {
        mDataSet.add(position,
                new AppPickerView.AppLabelInfo(AppPickerView.KEY_APP_SEPARATOR,
                        "", "").setSeparator(true));
        mDataSetFiltered.clear();
        mDataSetFiltered.addAll(mDataSet);

        refreshSectionMap();
        notifyItemInserted(position);
    }

    public void setOrder(int order) {
        mOrder = order;
        if (Build.VERSION.SDK_INT >= 24) {
            if (getAppLabelComparator(order) != null) {
                mDataSet.sort(getAppLabelComparator(order));
                mDataSetFiltered.sort(getAppLabelComparator(order));
            }
        }
        refreshSectionMap();
        notifyDataSetChanged();
    }

    private Comparator<AppPickerView.AppLabelInfo> getAppLabelComparator(int order) {
        return switch (order) {
            case AppPickerView.ORDER_ASCENDING -> APP_LABEL_ASCENDING;
            case AppPickerView.ORDER_ASCENDING_IGNORE_CASE -> APP_LABEL_ASCENDING_IGNORE_CASE;
            case AppPickerView.ORDER_DESCENDING -> APP_LABEL_DESCENDING;
            case AppPickerView.ORDER_DESCENDING_IGNORE_CASE -> APP_LABEL_DESCENDING_IGNORE_CASE;
            default -> null;
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final String packageName = mDataSetFiltered.get(position).getPackageName();
        final String activityName = mDataSetFiltered.get(position).getActivityName();

        AppPickerView.ViewHolder vh = (AppPickerView.ViewHolder) holder;
        if (!(holder instanceof AppPickerView.HeaderViewHolder)
                && !(holder instanceof AppPickerView.SeparatorViewHolder)) {
            if (mAppPickerIconLoader != null) {
                mAppPickerIconLoader.loadIcon(packageName, activityName, vh.getAppIcon());
            }

            final String label = mDataSetFiltered.get(position).getLabel();
            if (!mSearchText.isEmpty()) {
                SpannableString spannableString = new SpannableString(label);
                StringTokenizer stringTokenizer = new StringTokenizer(mSearchText);

                while (stringTokenizer.hasMoreTokens()) {
                    String nextToken = stringTokenizer.nextToken();
                    int offset = 0;
                    String remainingLabel = label;
                    do {
                        char[] prefixCharsForSpan = SeslTextUtilsReflector
                                .semGetPrefixCharForSpan(vh.getAppLabel().getPaint(), remainingLabel, nextToken.toCharArray());

                        if (prefixCharsForSpan != null) {
                            nextToken = new String(prefixCharsForSpan);
                        }

                        String lowerCaseLabel = remainingLabel.toLowerCase();
                        int tokenIndex;

                        if (remainingLabel.length() == lowerCaseLabel.length()) {
                            tokenIndex = lowerCaseLabel.indexOf(nextToken.toLowerCase());
                        } else {
                            tokenIndex = remainingLabel.indexOf(nextToken);
                        }

                        int length = nextToken.length() + tokenIndex;
                        if (tokenIndex < 0) {
                            break;
                        }

                        int tokenEndIndex = tokenIndex + offset;
                        offset += length;
                        spannableString.setSpan(
                                new ForegroundColorSpan(this.mForegroundColor), tokenEndIndex, offset, 17);
                        remainingLabel = remainingLabel.substring(length);
                        if (remainingLabel.toLowerCase().contains(nextToken.toLowerCase())) {
                            break;
                        }
                    } while (offset < MAX_OFFSET);
                }
                vh.getAppLabel().setText(spannableString);
                vh.getItem().setContentDescription(spannableString);
            } else {
                vh.getAppLabel().setText(label);
                vh.getItem().setContentDescription(label);
            }
        }

        onBindViewHolderAction(vh, position, packageName);

        if (mOnBindListener != null) {
            mOnBindListener.onBindViewHolder(vh, position, packageName);
        }
    }

    abstract void onBindViewHolderAction(AppPickerView.ViewHolder holder, int position,
            String packageName);

    @Override
    public int getItemCount() {
        return mDataSetFiltered.size();
    }

    @NonNull
    public AppPickerView.AppLabelInfo getAppInfo(int position) {
        return mDataSetFiltered.get(position);
    }

    public void setOnBindListener(@Nullable AppPickerView.OnBindListener listener) {
        mOnBindListener = listener;
    }

    public void setOnSearchFilterListener(@Nullable AppPickerView.OnSearchFilterListener listener) {
        mOnSearchFilterListener = listener;
    }

    protected boolean hasAllAppsInList() {
        return (mType == AppPickerView.TYPE_LIST_CHECKBOX_WITH_ALL_APPS
                || mType == AppPickerView.TYPE_LIST_SWITCH_WITH_ALL_APPS)
                && !mHideAllApps;
    }

    @Override
    public long getItemId(int position) {
        return mDataSetFiltered.get(position).hashCode();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final String searchText = constraint.toString();
                Filter.FilterResults results = new Filter.FilterResults();

                if (searchText.isEmpty()) {
                    mSearchText = "";
                    results.values = mDataSet;
                } else {
                    mSearchText = searchText;

                    ArrayList<AppPickerView.AppLabelInfo> dataSetFiltered = new ArrayList<>();
                    for (AppPickerView.AppLabelInfo labelInfo : mDataSet) {
                        if (!AppPickerView.ALL_APPS_STRING.equals(labelInfo.getPackageName())) {
                            final String label = labelInfo.getLabel();
                            if (!TextUtils.isEmpty(label)) {
                                StringTokenizer tokenizer = new StringTokenizer(searchText.toLowerCase());
                                boolean showItem = true;
                                String lowerCase = label.toLowerCase();

                                while (tokenizer.hasMoreTokens()) {
                                    if (!lowerCase.contains(tokenizer.nextToken())) {
                                        showItem = false;
                                        break;
                                    }
                                }

                                if (showItem) {
                                    dataSetFiltered.add(labelInfo);
                                }
                            }
                        }
                    }

                    results.values = dataSetFiltered;
                }
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mHideAllApps = !"".equals(mSearchText);
                mDataSetFiltered.clear();
                //noinspection unchecked
                mDataSetFiltered.addAll((ArrayList<AppPickerView.AppLabelInfo>) results.values);

                refreshSectionMap();
                notifyDataSetChanged();

                if (mOnSearchFilterListener != null) {
                    mOnSearchFilterListener.onSearchFilterCompleted(getItemCount());
                }
            }
        };
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sectionIndex >= mSections.length) {
            return 0;
        }
        return mSectionMap.get(mSections[sectionIndex]);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position >= mPositionToSectionIndex.length) {
            return 0;
        }
        return mPositionToSectionIndex[position];
    }

    @Override
    public Object[] getSections() {
        return mSections;
    }

    void refreshSectionMap() {
        mSectionMap.clear();
        ArrayList<String> sections = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            AlphabeticIndex.ImmutableIndex<Integer> immutableIndex = getBuckets();

            mPositionToSectionIndex = new int[mDataSetFiltered.size()];

            for (int i = 0; i < mDataSetFiltered.size(); i++) {
                String label = mDataSetFiltered.get(i).getLabel();
                if (TextUtils.isEmpty(label)) {
                    label = "";
                }
                label = immutableIndex.getBucket(immutableIndex.getBucketIndex(label)).getLabel();
                if (!mSectionMap.containsKey(label)) {
                    sections.add(label);
                    mSectionMap.put(label, i);
                }
                mPositionToSectionIndex[i] = sections.size() - 1;
            }

            mSections = new String[sections.size()];
            sections.toArray(mSections);
        }
    }

    @RequiresApi(api = 24)
    private AlphabeticIndex.ImmutableIndex<Integer> getBuckets() {
        LocaleList locales = mContext.getResources().getConfiguration().getLocales();
        if (locales.isEmpty()) {
            locales = new LocaleList(Locale.ENGLISH);
        }

        AlphabeticIndex<Integer> alphabeticIndex = new AlphabeticIndex<>(locales.get(0));
        for (int i = 1; i < locales.size(); i++) {
            alphabeticIndex.addLabels(locales.get(i));
        }
        alphabeticIndex.addLabels(Locale.ENGLISH);

        AlphabeticIndex.ImmutableIndex<Integer> immutableIndex =
                alphabeticIndex.buildImmutableIndex();
        return immutableIndex;
    }

    protected float limitFontScale(@NonNull TextView textView) {
        final float currentFontScale
                = textView.getResources().getConfiguration().fontScale;
        int i = (Float.compare(currentFontScale, 1.3f));
        final float textSize = textView.getTextSize();
        return i > 0 ? (textSize / currentFontScale) * 1.3f : textSize;
    }

    protected void limitFontLarge(@NonNull TextView textView) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, limitFontScale(textView));
    }

    protected void limitFontLarge2LinesHeight(@NonNull TextView textView) {
        float limitFontScale = limitFontScale(textView);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, limitFontScale);
        textView.setMinHeight(Math.round((limitFontScale * 2.0f) + 0.5f));
    }

    private static final Comparator<AppPickerView.AppLabelInfo> APP_LABEL_ASCENDING
            = (a, b) -> {
                Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.TERTIARY);
                return collator.compare(a.getLabel(), b.getLabel());
            };

    private static final Comparator<AppPickerView.AppLabelInfo> APP_LABEL_ASCENDING_IGNORE_CASE
            = (a, b) -> {
                Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(a.getLabel(), b.getLabel());
            };

    private static final Comparator<AppPickerView.AppLabelInfo> APP_LABEL_DESCENDING
            = (a, b) -> {
                Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.TERTIARY);
                return collator.compare(b.getLabel(), a.getLabel());
            };

    private static final Comparator<AppPickerView.AppLabelInfo> APP_LABEL_DESCENDING_IGNORE_CASE
            = (a, b) -> {
                Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(b.getLabel(), a.getLabel());
            };
}
