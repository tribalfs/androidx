// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from BrowseSupportFragmentTest.java.  DO NOT MODIFY. */

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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v17.leanback.testutils.PollingCheck;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
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
public class BrowseFragmentTest {

    static final String TAG = "BrowseFragmentTest";
    static final long WAIT_TRANSIITON_TIMEOUT = 10000;

    @Rule
    public ActivityTestRule<BrowseFragmentTestActivity> activityTestRule =
            new ActivityTestRule<>(BrowseFragmentTestActivity.class, false, false);
    private BrowseFragmentTestActivity mActivity;

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
                    return mActivity.getBrowseTestFragment() != null
                            && mActivity.getBrowseTestFragment().mEntranceTransitionEnded;
                } else {
                    // when entrance transition not supported, wait main fragment loaded.
                    return mActivity.getBrowseTestFragment() != null
                            && mActivity.getBrowseTestFragment().getMainFragment() != null;
                }
            }
        });
    }

    void waitForHeaderTransitionFinished() {
        View row = mActivity.getBrowseTestFragment().getRowsFragment().getRowViewHolder(
                mActivity.getBrowseTestFragment().getSelectedPosition()).view;
        PollingCheck.waitFor(WAIT_TRANSIITON_TIMEOUT, new PollingCheck.ViewStableOnScreen(row));
    }

    @Test
    public void testTwoBackKeysWithBackStack() throws Throwable {
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , true);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        assertNotNull(mActivity.getBrowseTestFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        waitForHeaderTransitionFinished();
        sendKeys(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testTwoBackKeysWithoutBackStack() throws Throwable {
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        assertNotNull(mActivity.getBrowseTestFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
        waitForHeaderTransitionFinished();
        sendKeys(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testPressRightBeforeMainFragmentCreated() throws Throwable {
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        mActivity = activityTestRule.launchActivity(intent);

        assertNull(mActivity.getBrowseTestFragment().getMainFragment());
        sendKeys(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public static class MyRow extends Row {
    }

    public static class MyFragment extends Fragment implements
            BrowseFragment.MainFragmentAdapterProvider {
        BrowseFragment.MainFragmentAdapter mMainFragmentAdapter = new BrowseFragment
                .MainFragmentAdapter(this);

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            return new FrameLayout(container.getContext());
        }

        @Override
        public BrowseFragment.MainFragmentAdapter getMainFragmentAdapter() {
            return mMainFragmentAdapter;
        }
    }

    public static class MyFragmentFactory extends
            BrowseFragment.FragmentFactory<MyFragment> {
        public MyFragment createFragment(Object row) {
            return new MyFragment();
        }
    }

    @Test
    public void testPressCenterBeforeMainFragmentCreated() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, 0);
        mActivity = activityTestRule.launchActivity(intent);

        final BrowseFragment fragment = mActivity.getBrowseTestFragment();
        fragment.getMainFragmentRegistry().registerFragment(MyRow.class, new MyFragmentFactory());

        final ArrayObjectAdapter adapter = new ArrayObjectAdapter(new RowHeaderPresenter());
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

    @Test
    public void testSelectCardOnARow() throws Throwable {
        final int selectRow = 10;
        final int selectItem = 20;
        Intent intent = new Intent();
        final long dataLoadingDelay = 1000;
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , true);
        mActivity = activityTestRule.launchActivity(intent);

        waitForEntranceTransitionFinished();

        Presenter.ViewHolderTask itemTask = Mockito.spy(
                new ItemSelectionTask(mActivity, selectRow));

        final ListRowPresenter.SelectItemViewHolderTask task =
                new ListRowPresenter.SelectItemViewHolderTask(selectItem);
        task.setItemTask(itemTask);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getBrowseTestFragment().setSelectedPosition(selectRow, true, task);
            }
        });

        verify(itemTask, timeout(5000).times(1)).run(any(Presenter.ViewHolder.class));

        activityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ListRowPresenter.ViewHolder row = (ListRowPresenter.ViewHolder) mActivity
                        .getBrowseTestFragment().getRowsFragment().getRowViewHolder(selectRow);
                assertNotNull(dumpRecyclerView(mActivity.getBrowseTestFragment().getGridView()), row);
                assertNotNull(row.getGridView());
                assertEquals(selectItem, row.getGridView().getSelectedPosition());
            }
        });
    }

    @Test
    public void activityRecreate_notCrash() throws Throwable {
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_ADD_TO_BACKSTACK , false);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_SET_ADAPTER_AFTER_DATA_LOAD, true);
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
        final long dataLoadingDelay = 1000;
        Intent intent = new Intent();
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_LOAD_DATA_DELAY, dataLoadingDelay);
        intent.putExtra(BrowseFragmentTestActivity.EXTRA_HEADERS_STATE,
                BrowseFragment.HEADERS_DISABLED);
        mActivity = activityTestRule.launchActivity(intent);
        waitForEntranceTransitionFinished();
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mActivity.getBrowseTestFragment().getGridView() != null
                        && mActivity.getBrowseTestFragment().getGridView().getChildCount() > 0;
            }
        });
    }

    private void sendKeys(int ...keys) {
        for (int i = 0; i < keys.length; i++) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keys[i]);
        }
    }

    public static class ItemSelectionTask extends Presenter.ViewHolderTask {

        private final BrowseFragmentTestActivity activity;
        private final int expectedRow;

        public ItemSelectionTask(BrowseFragmentTestActivity activity, int expectedRow) {
            this.activity = activity;
            this.expectedRow = expectedRow;
        }

        @Override
        public void run(Presenter.ViewHolder holder) {
            android.util.Log.d(TAG, dumpRecyclerView(activity.getBrowseTestFragment()
                    .getGridView()));
            android.util.Log.d(TAG, "Row " + expectedRow + " " + activity.getBrowseTestFragment()
                    .getRowsFragment().getRowViewHolder(expectedRow), new Exception());
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
