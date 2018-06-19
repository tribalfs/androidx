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

package com.example.androidx.slice.demos;

import static androidx.slice.core.SliceHints.INFINITY;
import static androidx.slice.test.SampleSliceProvider.URI_PATHS;
import static androidx.slice.test.SampleSliceProvider.getUri;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.SliceMetadata;
import androidx.slice.SliceUtils;
import androidx.slice.widget.EventInfo;
import androidx.slice.widget.SliceLiveData;
import androidx.slice.widget.SliceView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Example use of SliceView. Uses a search bar to select/auto-complete a slice uri which is
 * then displayed in the selected mode with SliceView.
 */
public class SliceBrowser extends AppCompatActivity implements SliceView.OnSliceActionListener {

    private static final String TAG = "SliceBrowser";

    private static final String SLICE_METADATA_KEY = "android.metadata.SLICE_URI";
    private static final boolean TEST_INTENT = false;

    private ArrayList<Uri> mSliceUris = new ArrayList<Uri>();
    private int mSelectedMode;
    private ViewGroup mContainer;
    private SearchView mSearchView;
    private SimpleCursorAdapter mAdapter;
    private SubMenu mTypeMenu;
    private LiveData<Slice> mSliceLiveData;

    private SliceView mSliceView;
    private boolean mScrollingEnabled = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout);

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);

        // Shows the slice
        mContainer = findViewById(R.id.slice_preview);
        mSearchView = findViewById(R.id.search_view);

        mSliceView = findViewById(R.id.slice_view);

        final String[] from = new String[]{"uri"};
        final int[] to = new int[]{android.R.id.text1};
        mAdapter = new SimpleCursorAdapter(this, R.layout.simple_list_item_1,
                null, from, to, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mSearchView.setSuggestionsAdapter(mAdapter);
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionClick(int position) {
                mSearchView.setQuery(((Cursor) mAdapter.getItem(position)).getString(1), true);
                return true;
            }

            @Override
            public boolean onSuggestionSelect(int position) {
                mSearchView.setQuery(((Cursor) mAdapter.getItem(position)).getString(1), true);
                return true;
            }
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                addSlice(Uri.parse(s));
                mSearchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                populateAdapter(s);
                return false;
            }
        });

        mSelectedMode = (savedInstanceState != null)
                ? savedInstanceState.getInt("SELECTED_MODE", SliceView.MODE_LARGE)
                : SliceView.MODE_LARGE;
        if (savedInstanceState != null) {
            mSearchView.setQuery(savedInstanceState.getString("SELECTED_QUERY"), true);
            mScrollingEnabled = savedInstanceState.getBoolean("SCROLLING_ENABLED");
        }

        // TODO: Listen for changes.
        updateAvailableSlices();
        initSliceView(mSliceView);
        if (TEST_INTENT) {
            addSlice(new Intent("androidx.intent.SLICE_ACTION").setPackage(getPackageName()));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTypeMenu = menu.addSubMenu("Type");
        mTypeMenu.setIcon(R.drawable.ic_large);
        mTypeMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mTypeMenu.add("Shortcut");
        mTypeMenu.add("Small");
        mTypeMenu.add("Large");
        menu.add("Open");
        menu.add("Toggle scrolling");
        menu.add("Show serialized");
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getTitle().toString()) {
            case "Shortcut":
                mTypeMenu.setIcon(R.drawable.ic_shortcut);
                mSelectedMode = SliceView.MODE_SHORTCUT;
                updateSliceModes();
                return true;
            case "Small":
                mTypeMenu.setIcon(R.drawable.ic_small);
                mSelectedMode = SliceView.MODE_SMALL;
                updateSliceModes();
                return true;
            case "Large":
                mTypeMenu.setIcon(R.drawable.ic_large);
                mSelectedMode = SliceView.MODE_LARGE;
                updateSliceModes();
                return true;
            case "Open":
                SliceSelectionDialog.create(this, uri -> addSlice(uri));
                return true;
            case "Toggle scrolling":
                mScrollingEnabled = !mScrollingEnabled;
                mSliceView.setScrollable(mScrollingEnabled);
                String message = "Scrolling " + (mScrollingEnabled ? "enabled" : "disabled");
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                return true;
            case "Show serialized":
                Slice currentSlice = mSliceView.getSlice();
                setSlice(null);
                if (currentSlice != null) {
                    setSlice(serAndUnSer(currentSlice, getApplicationContext()));
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("SELECTED_MODE", mSelectedMode);
        outState.putString("SELECTED_QUERY", mSearchView.getQuery().toString());
        outState.putBoolean("SCROLLING_ENABLED", mScrollingEnabled);
    }

    private void updateAvailableSlices() {
        mSliceUris.clear();
        List<PackageInfo> packageInfos = getPackageManager()
                .getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
        for (PackageInfo pi : packageInfos) {
            ActivityInfo[] activityInfos = pi.activities;
            if (activityInfos != null) {
                for (ActivityInfo ai : activityInfos) {
                    if (ai.metaData != null) {
                        String sliceUri = ai.metaData.getString(SLICE_METADATA_KEY);
                        if (sliceUri != null) {
                            mSliceUris.add(Uri.parse(sliceUri));
                        }
                    }
                }
            }
        }
        for (int i = 0; i < URI_PATHS.length; i++) {
            mSliceUris.add(getUri(URI_PATHS[i], getApplicationContext()));
        }
        populateAdapter(String.valueOf(mSearchView.getQuery()));
    }

    private void setSlice(Slice s) {
        if (mSliceLiveData != null) {
            mSliceLiveData.removeObservers(this);
        }
        Toast.makeText(getApplicationContext(),
                "Showing serialized slice", Toast.LENGTH_SHORT).show();
        mSliceView.setSlice(s);
    }

    private void addSlice(Intent intent) {
        if (mSliceLiveData != null) {
            mSliceLiveData.removeObservers(this);
        }
        mSliceView.setTag(intent);
        mSliceLiveData = SliceLiveData.fromIntent(this, intent);
        mSliceView.setMode(mSelectedMode);
        mSliceLiveData.observe(this, mSliceView);
    }

    private void addSlice(Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            if (mSliceLiveData != null) {
                mSliceLiveData.removeObservers(this);
            }
            mSliceView.setTag(uri);
            mSliceLiveData = SliceLiveData.fromUri(this, uri);
            mSliceLiveData.observe(this, slice -> {
                if (slice == null) {
                    Log.w(TAG, "Slice is null");
                    mSliceView.setSlice(null);
                    Toast.makeText(this, "Invalid slice URI", Toast.LENGTH_SHORT).show();
                    return;
                }
                mSliceView.setSlice(slice);
                SliceMetadata metadata = SliceMetadata.from(this, slice);
                long expiry = metadata.getExpiry();
                if (expiry != INFINITY) {
                    // Shows the updated text after the TTL expires.
                    mSliceView.postDelayed(() -> {
                        if (mSliceView.getSlice() != null
                                && mSliceView.getSlice().getUri().equals(slice.getUri())) {
                            mSliceView.setSlice(slice);
                        }
                    }, expiry - System.currentTimeMillis() + 15);
                }
            });
            mSliceLiveData.observe(this, slice -> Log.d(TAG, "Slice: " + slice));
        } else {
            Log.w(TAG, "Invalid uri, skipping slice: " + uri);
            mSliceView.setSlice(null);
            Toast.makeText(this, "Invalid slice URI", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSliceModes() {
        final int count = mContainer.getChildCount();
        for (int i = 0; i < count; i++) {
            ((SliceView) mContainer.getChildAt(i)).setMode(mSelectedMode);
        }
    }

    private void populateAdapter(String query) {
        final MatrixCursor c = new MatrixCursor(new String[]{BaseColumns._ID, "uri"});
        ArrayMap<String, Integer> ranking = new ArrayMap<>();
        ArrayList<String> suggestions = new ArrayList();
        for (Uri uri : mSliceUris) {

            String uriString = uri.toString();
            if (uriString.contains(query)) {
                ranking.put(uriString, uriString.indexOf(query));
                suggestions.add(uriString);
            }
        }
        Collections.sort(suggestions, (o1, o2) ->
                Integer.compare(ranking.get(o1), ranking.get(o2)));
        for (int i = 0; i < suggestions.size(); i++) {
            c.addRow(new Object[]{i, suggestions.get(i)});
        }
        mAdapter.changeCursor(c);
    }

    @Override
    public void onSliceAction(@NonNull EventInfo info, @NonNull SliceItem item) {
        Log.w(TAG, "onSliceAction, info: " + info);
        Log.w(TAG, "onSliceAction, sliceItem: \n" + item);
    }

    private void initSliceView(SliceView sliceView) {
        sliceView.setOnSliceActionListener(this);
        sliceView.setScrollable(mScrollingEnabled);
        sliceView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(getApplicationContext(), "LONGPRESS !!", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        sliceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "No action on slice, fallthrough click",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Slice serAndUnSer(Slice s, Context context) {
        try {
            Log.d(TAG, "Serializing: " + s);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            SliceUtils.serializeSlice(s, context, outputStream,
                    new SliceUtils.SerializeOptions()
                            .setMaxImageWidth(500)
                            .setMaxImageHeight(500)
                            .setImageMode(SliceUtils.SerializeOptions.MODE_CONVERT)
                            .setActionMode(SliceUtils.SerializeOptions.MODE_CONVERT)
                            .setImageConversionFormat(Bitmap.CompressFormat.JPEG, 50));

            byte[] bytes = outputStream.toByteArray();
            Log.d(TAG, "Serialized length: " + bytes.length);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            return SliceUtils.parseSlice(context, inputStream, "UTF-8",
                    new SliceUtils.SliceActionListener() {
                        @Override
                        public void onSliceAction(Uri actionUri, Context context, Intent intent) { }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
