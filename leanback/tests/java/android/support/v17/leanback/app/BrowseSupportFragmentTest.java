/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.v17.leanback.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v17.leanback.testutils.PollingCheck;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v17.leanback.widget.ViewHolderTask;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BrowseSupportFragmentTest {

    static final String TAG = "BrowseSupportFragmentTest";
    static final long WAIT_TRANSIITON_TIMEOUT = 10000;

    @Rule
    public ActivityTestRule<BrowseSupportFragmentTestActivity> activityTestRule =
            new ActivityTestRule<>(BrowseSupportFragmentTestActivity.class, false, false);
    private BrowseSupportFragmentTestActivity mActivity;

    @After
    public void afterTest() throws Throwable {
        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActivity != null) {
                    mActivity.finish();
                    mActivity = null;
                }
            }
        });
    }

    void waitForEntranceTransitionFinished() {
        PollingCheck.waitFor(WAIT_TRANSIITON_TIMEOUT, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                if (Build.VERSION.SDK_INT >= 21) {
                    return mActivity.getBrowseTestSupportFragment() != null
                            && mActivity.getBrowseTestSupportFragment().mEntranceTransitionEnded;
                } else {
                    // when entrance transition not supported, wait main fragment loaded.
                    return mActivity.getBrowseTestSupportFragment() != null
                            && mActivity.getBrowseTestSupportFragment().getMainFragment() != null;
                }
            }
        });
    }

    void waitForHeaderTransitionFinished() {
        View row = mActivity.getBrowseTestSupportFragment().getRowsSupportFragment().getRowViewHolder(
                mActivity.getBrowseTestSupportFragment().getSelectedPosition()).view;
        PollingCheck.waitFor(WAIT_TRANSIITON_TIMEOUT, new PollingCheck.ViewStableOnScreen(row));
        PollingCheck.waitFor(WAIT_TRANSIITON_TIMEOUT, new PollingCheck.PollingCheckCondition() {
            public boolean canProceed() {
                return !mActivity.getBrowseTestSupportFragment().isInHeadersTransition();
            }
        });
    }

    @Test
    public void testTouchMode() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , true);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY , 0L);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        ListRowPresenter.ViewHolder rowVh = (ListRowPresenter.ViewHolder) mActivity
                .getBrowseTestSupportFragment().getRowsSupportFragment().getRowViewHolder(0);
        View card = rowVh.getGridView().getChildAt(0);
        tapView(card);
        waitForHeaderTransitionFinished();
        assertTrue(card.hasFocus());
        assertTrue(card.isInTouchMode());
        sendKeys(KeyEvent.KEYCODE_BACK);
        waitForHeaderTransitionFinished();
        assertTrue((mActivity.getBrowseTestSupportFragment().getHeadersSupportFragment()
                .getVerticalGridView().getChildAt(0)).hasFocus());
    }

    @Test
    public void testTwoBackKeysWithBackStack() throws Throwable {
        final long dataLoadingDelay = 0L;
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , true);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        assertNotNull(mActivity.getBrowseTestSupportFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        waitForHeaderTransitionFinished();
        sendKeys(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testTwoBackKeysWithoutBackStack() throws Throwable {
        final long dataLoadingDelay = 0L;
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        assertNotNull(mActivity.getBrowseTestSupportFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        waitForHeaderTransitionFinished();
        sendKeys(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testPressRightBeforeMainFragmentCreated() throws Throwable {
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        mActivity = activityTestRule.launchActivity(intent);

        assertNull(mActivity.getBrowseTestSupportFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public static class MyRow extends Row {
    }

    public static class MyFragment extends Fragment implements
            BrowseSupportFragment.MainFragmentAdapterProvider {
        BrowseSupportFragment.MainFragmentAdapter mMainFragmentAdapter = new BrowseSupportFragment
                .MainFragmentAdapter(this);

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return new FrameLayout(container.getContext());
        }

        @Override
        public BrowseSupportFragment.MainFragmentAdapter getMainFragmentAdapter() {
            return mMainFragmentAdapter;
        }
    }

    public static class MyFragmentFactory extends
            BrowseSupportFragment.FragmentFactory<MyFragment> {
        public MyFragment createFragment(Object row) {
            return new MyFragment();
        }
    }

    @Test
    public void testPressCenterBeforeMainFragmentCreated() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, 0L);
        mActivity = activityTestRule.launchActivity(intent);

        final BrowseSupportFragment fragment = mActivity.getBrowseTestSupportFragment();
        fragment.getMainFragmentRegistry().registerFragment(MyRow.class, new MyFragmentFactory());

        final ArrayObjectAdapter adapter = new ArrayObjectAdapter(new RowPresenter() {
            protected ViewHolder createRowViewHolder(ViewGroup parent) {
                View view = new FrameLayout(parent.getContext());
                return new RowPresenter.ViewHolder(view);
            }
        });
        adapter.add(new MyRow());
        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fragment.setAdapter(adapter);
            }
        });
        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                KeyEvent kv;
                kv = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
                fragment.getView().dispatchKeyEvent(kv);
                kv = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER);
                fragment.getView().dispatchKeyEvent(kv);
            }
        });
    }

    // copy of RowsSupportFragment.setSelectedPosition() for debugging purpose
    static void setSelectedPosition(RowsSupportFragment fragment,
            int rowPosition, boolean smooth,
            final Presenter.ViewHolderTask rowHolderTask) {
        VerticalGridView verticalView = fragment.getVerticalGridView();
        if (verticalView == null) {
            return;
        }
        ViewHolderTask task = null;
        if (rowHolderTask != null) {
            // This task will execute once the scroll completes. Once the scrolling finishes,
            // we will get a success callback to update selected row position. Since the
            // update to selected row position happens in a post, we want to ensure that this
            // gets called after that.
            task = new ViewHolderTask() {
                @Override
                public void run(final RecyclerView.ViewHolder rvh) {
                    Log.d(TAG, "posting rowHolderTask for " + rvh, new Exception());
                    rvh.itemView.post(new Runnable() {
                        @Override
                        public void run() {
                            rowHolderTask.run(RowsSupportFragment
                                    .getRowViewHolder((ItemBridgeAdapter.ViewHolder) rvh));
                        }
                    });
                }
            };
        }

        if (smooth) {
            verticalView.setSelectedPositionSmooth(rowPosition, task);
        } else {
            verticalView.setSelectedPosition(rowPosition, task);
        }
    }

    // copy of ListRowPresenter.SelectItemViewHolderTask for debugging purpose
    static class SelectItemViewHolderTask extends Presenter.ViewHolderTask {

        private int mItemPosition;
        private boolean mSmoothScroll = true;
        Presenter.ViewHolderTask mItemTask;

        SelectItemViewHolderTask(int itemPosition) {
            setItemPosition(itemPosition);
        }

        public void setItemPosition(int itemPosition) {
            mItemPosition = itemPosition;
        }

        public int getItemPosition() {
            return mItemPosition;
        }

        public void setSmoothScroll(boolean smoothScroll) {
            mSmoothScroll = smoothScroll;
        }

        public boolean isSmoothScroll() {
            return mSmoothScroll;
        }

        public Presenter.ViewHolderTask getItemTask() {
            return mItemTask;
        }

        public void setItemTask(Presenter.ViewHolderTask itemTask) {
            mItemTask = itemTask;
        }

        @Override
        public void run(Presenter.ViewHolder holder) {
            Log.d(TAG, "run on rowViewHolder: " + holder, new Exception());
            if (holder instanceof ListRowPresenter.ViewHolder) {
                Log.d(TAG, "run on row: " + ((ListRowPresenter.ViewHolder) holder).getRow());
                HorizontalGridView gridView = ((ListRowPresenter.ViewHolder) holder).getGridView();
                android.support.v17.leanback.widget.ViewHolderTask task = null;
                if (mItemTask != null) {
                    task = new android.support.v17.leanback.widget.ViewHolderTask() {
                        final Presenter.ViewHolderTask mSavedItemTask = mItemTask;
                        @Override
                        public void run(RecyclerView.ViewHolder rvh) {
                            Log.d(TAG, "run on " + mSavedItemTask, new Exception());
                            ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder) rvh;
                            mSavedItemTask.run(ibvh.getViewHolder());
                        }
                    };
                }
                Log.d(TAG, "searching item " + mItemPosition + " " + mItemTask);
                if (isSmoothScroll()) {
                    gridView.setSelectedPositionSmooth(mItemPosition, task);
                } else {
                    gridView.setSelectedPosition(mItemPosition, task);
                }
            }
        }
    }

    @Test
    public void testSelectCardOnARow() throws Throwable {
        final int selectRow = 10;
        final int selectItem = 20;
        Intent intent = new Intent();
        final long dataLoadingDelay = 0L;
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , true);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        Presenter.ViewHolderTask itemTask = Mockito.spy(
                new ItemSelectionTask(mActivity, selectRow));

        final SelectItemViewHolderTask task =
                new SelectItemViewHolderTask(selectItem);
        task.setItemTask(itemTask);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getBrowseTestSupportFragment().startHeadersTransition(false);
                setSelectedPosition(mActivity.getBrowseTestSupportFragment()
                                .getRowsSupportFragment(), selectRow, true, task);
            }
        });

        verify(itemTask, timeout(5000).times(1)).run(any(Presenter.ViewHolder.class));

        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ListRowPresenter.ViewHolder row = (ListRowPresenter.ViewHolder) mActivity
                        .getBrowseTestSupportFragment().getRowsSupportFragment().getRowViewHolder(selectRow);
                assertNotNull(dumpRecyclerView(mActivity.getBrowseTestSupportFragment().getGridView()), row);
                assertNotNull(row.getGridView());
                assertEquals(selectItem, row.getGridView().getSelectedPosition());
            }
        });
    }

    @Test
    public void activityRecreate_notCrash() throws Throwable {
        final long dataLoadingDelay = 0L;
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_SET_ADAPTER_AFTER_DATA_LOAD, true);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        InstrumentationRegistry.getInstrumentation().callActivityOnRestart(mActivity);
        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.recreate();
            }
        });
    }


    @Test
    public void lateLoadingHeaderDisabled() throws Throwable {
        final long dataLoadingDelay = 0L;
        Intent intent = new Intent();
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseSupportFragmentTestActivity.EXTRA_HEADERS_STATE,
                BrowseSupportFragment.HEADERS_DISABLED);
        mActivity = activityTestRule.launchActivity(intent);
        waitForEntranceTransitionFinished();
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mActivity.getBrowseTestSupportFragment().getGridView() != null
                        && mActivity.getBrowseTestSupportFragment().getGridView().getChildCount() > 0;
            }
        });
    }

    static void tapView(View v) {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        int[] xy = new int[2];
        v.getLocationOnScreen(xy);

        final int viewWidth = v.getWidth();
        final int viewHeight = v.getHeight();

        final float x = xy[0] + (viewWidth / 2.0f);
        float y = xy[1] + (viewHeight / 2.0f);

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();

        MotionEvent event = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        inst.sendPointerSync(event);
        inst.waitForIdleSync();

        eventTime = SystemClock.uptimeMillis();
        event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
        inst.sendPointerSync(event);
        inst.waitForIdleSync();
    }

    private void sendKeys(int ...keys) {
        for (int i = 0; i < keys.length; i++) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keys[i]);
        }
    }

    public static class ItemSelectionTask extends Presenter.ViewHolderTask {

        private final BrowseSupportFragmentTestActivity activity;
        private final int expectedRow;

        public ItemSelectionTask(BrowseSupportFragmentTestActivity activity, int expectedRow) {
            this.activity = activity;
            this.expectedRow = expectedRow;
        }

        @Override
        public void run(Presenter.ViewHolder holder) {
            android.util.Log.d(TAG, dumpRecyclerView(activity.getBrowseTestSupportFragment()
                    .getGridView()));
            android.util.Log.d(TAG, "Row " + expectedRow + " " + activity.getBrowseTestSupportFragment()
                    .getRowsSupportFragment().getRowViewHolder(expectedRow), new Exception());
        }
    }

    static String dumpRecyclerView(RecyclerView recyclerView) {
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            ItemBridgeAdapter.ViewHolder vh = (ItemBridgeAdapter.ViewHolder)
                    recyclerView.getChildViewHolder(child);
            b.append("child").append(i).append(":").append(vh);
            if (vh != null) {
                b.append(",").append(vh.getViewHolder());
            }
            b.append(";");
        }
        return b.toString();
    }
}
