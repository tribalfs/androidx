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
package android.support.v17.leanback.app;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ListView;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * An internal base class for a fragment containing a list of row headers.
 */
abstract class BaseRowFragment extends Fragment {
    private ObjectAdapter mAdapter;
    private ListView mListView;
    private PresenterSelector mPresenterSelector;
    private ItemBridgeAdapter mBridgeAdapter;
    private int mSelectedPosition = -1;

    abstract protected int getLayoutResourceId();

    private final OnChildSelectedListener mRowSelectedListener = new OnChildSelectedListener() {
        @Override
        public void onChildSelected(ViewGroup parent, View view, int position, long id) {
            onRowSelected(parent, view, position, id);
        }
    };

    protected void onRowSelected(ViewGroup parent, View view, int position, long id) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mListView = (ListView) inflater.inflate(getLayoutResourceId(), container, false);
        return mListView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (mBridgeAdapter != null) {
            mListView.setAdapter(mBridgeAdapter);
            if (mSelectedPosition != -1) {
                mListView.setSelectedPosition(mSelectedPosition);
            }
        }
        mListView.setOnChildSelectedListener(mRowSelectedListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mListView = null;
    }

    /**
     * Set the presenter selector used to create and bind views.
     */
    public final void setPresenterSelector(PresenterSelector presenterSelector) {
        mPresenterSelector = presenterSelector;
        updateAdapter();
    }

    /**
     * Get the presenter selector used to create and bind views.
     */
    public final PresenterSelector getPresenterSelector() {
        return mPresenterSelector;
    }

    /**
     * Sets the adapter for the fragment.
     */
    public final void setAdapter(ObjectAdapter rowsAdapter) {
        mAdapter = rowsAdapter;
        updateAdapter();
    }

    /**
     * Returns the list of rows.
     */
    public final ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Returns the bridge adapter.
     */
    protected final ItemBridgeAdapter getBridgeAdapter() {
        return mBridgeAdapter;
    }

    /**
     * Set the selected item position.
     */
    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
        if(mListView != null && mListView.getAdapter() != null) {
            mListView.setSelectedPositionSmooth(position);
        }
    }

    final ListView getListView() {
        return mListView;
    }

    protected void updateAdapter() {
        mBridgeAdapter = null;

        if (mAdapter != null) {
            // If presenter selector is null, adapter ps will be used
            mBridgeAdapter = new ItemBridgeAdapter(mAdapter, mPresenterSelector);
        }
        if (mListView != null) {
            mListView.setAdapter(mBridgeAdapter);
            if (mBridgeAdapter != null && mSelectedPosition != -1) {
                mListView.setSelectedPosition(mSelectedPosition);
            }
        }
    }

    protected Object getItem(Row row, int position) {
        if (row instanceof ListRow) {
            return ((ListRow) row).getAdapter().get(position);
        } else {
            return null;
        }
    }
}
