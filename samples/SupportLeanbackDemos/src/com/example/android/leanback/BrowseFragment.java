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
package com.example.android.leanback;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityOptionsCompat;
import android.app.FragmentManager;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.ArrayList;

public class BrowseFragment extends android.support.v17.leanback.app.BrowseFragment {
    private static final String TAG = "leanback.BrowseFragment";

    private static final boolean TEST_ENTRANCE_TRANSITION = true;
    private static final int NUM_ROWS = 10;
    // Row heights default to wrap content
    private static final boolean USE_FIXED_ROW_HEIGHT = false;

    private ArrayObjectAdapter mRowsAdapter;
    private BackgroundHelper mBackgroundHelper = new BackgroundHelper();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.ic_title));
        setTitle("Leanback Sample App");
        setHeadersState(HEADERS_ENABLED);

        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setupRows();
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new OnItemViewSelectedListener() {
            @Override
            public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                    RowPresenter.ViewHolder rowViewHolder, Row row) {
                Log.i(TAG, "onItemSelected: " + item + " row " + row);

                if (isShowingHeaders()) {
                    mBackgroundHelper.setBackground(getActivity(), null);
                }
                else if (item instanceof PhotoItem) {
                    mBackgroundHelper.setBackground(
                            getActivity(), ((PhotoItem) item).getImageResourceId());
                }
            }
        });
        if (TEST_ENTRANCE_TRANSITION) {
            // don't run entrance transition if Activity is restored.
            if (savedInstanceState == null) {
                prepareEntranceTransition();
            }
            // simulate delay loading data
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    startEntranceTransition();
                }
            }, 2000);
        }
    }

    private void setupRows() {
        ListRowPresenter lrp = new ListRowPresenter();

        if (USE_FIXED_ROW_HEIGHT) {
            lrp.setRowHeight(CardPresenter.getRowHeight(getActivity()));
            lrp.setExpandedRowHeight(CardPresenter.getExpandedRowHeight(getActivity()));
        }

        mRowsAdapter = new ArrayObjectAdapter(lrp);

        // For good performance, it's important to use a single instance of
        // a card presenter for all rows using that presenter.
        final CardPresenter cardPresenter = new CardPresenter();

        for (int i = 0; i < NUM_ROWS; ++i) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
            listRowAdapter.add(new PhotoItem("Hello world", R.drawable.gallery_photo_1));
            listRowAdapter.add(new PhotoItem("This is a test", "Only a test", R.drawable.gallery_photo_2));
            listRowAdapter.add(new PhotoItem("Android TV", "by Google", R.drawable.gallery_photo_3));
            listRowAdapter.add(new PhotoItem("Leanback", R.drawable.gallery_photo_4));
            listRowAdapter.add(new PhotoItem("Hello world", R.drawable.gallery_photo_5));
            listRowAdapter.add(new PhotoItem("This is a test", "Only a test", R.drawable.gallery_photo_6));
            listRowAdapter.add(new PhotoItem("Android TV", "open RowsActivity", R.drawable.gallery_photo_7));
            listRowAdapter.add(new PhotoItem("Leanback", "open BrowseActivity", R.drawable.gallery_photo_8));
            HeaderItem header = new HeaderItem(i, "Row " + i);
            mRowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        setAdapter(mRowsAdapter);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                RowPresenter.ViewHolder rowViewHolder, Row row) {
            ConfirmPhotoView step = new ConfirmPhotoView((PhotoItem)item,
                    (ImageCardView)itemViewHolder.view);
            GuidedStepFragment.add(getFragmentManager(), step);
        }
    }

    private class ConfirmPhotoView extends GuidedStepFragment {
        private static final int CONTINUE = 1;
        private static final int BACK = 2;

        private PhotoItem mItem;
        private ImageCardView mImageCardView;

        private void addAction(List<GuidedAction> actions, long id, String title, String desc) {
            actions.add(new GuidedAction.Builder()
                    .id(id)
                    .title(title)
                    .description(desc)
                    .build());
        }

        ConfirmPhotoView(PhotoItem item, ImageCardView imageCardView) {
            mItem = item;
            mImageCardView = imageCardView;
        }

        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = "Confirm";
            String breadcrumb = "BrowseFragment";
            String description = "Confirm intent to view this photo";
            Drawable icon = getActivity().getDrawable(R.drawable.ic_main_icon);
            return new Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            addAction(actions, CONTINUE, "Continue", "Let's do it");
            addAction(actions, BACK, "Cancel", "Nevermind");
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            FragmentManager fm = getFragmentManager();
            if (action.getId() == CONTINUE) {
                launchPhotoActivity(mItem, mImageCardView);
            } else {
                fm.popBackStack();
            }
        }
    }

    private void launchPhotoActivity(PhotoItem item, ImageCardView imageCardView) {
        Intent intent;
        Bundle bundle;
        if ( item.getImageResourceId() == R.drawable.gallery_photo_8) {
            intent = new Intent(getActivity(), BrowseActivity.class);
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                    .toBundle();
        } else if ( item.getImageResourceId() == R.drawable.gallery_photo_7) {
            intent = new Intent(getActivity(), RowsActivity.class);
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity())
                    .toBundle();
        } else {
            intent = new Intent(getActivity(), DetailsActivity.class);
            intent.putExtra(DetailsActivity.EXTRA_ITEM, item);
            bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(),
                    imageCardView.getMainImageView(),
                    DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
        }
        getActivity().startActivity(intent, bundle);
    }
}
