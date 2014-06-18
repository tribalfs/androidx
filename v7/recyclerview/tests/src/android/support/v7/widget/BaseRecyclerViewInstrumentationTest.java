/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v7.widget;

import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

abstract public class BaseRecyclerViewInstrumentationTest extends
        ActivityInstrumentationTestCase2<TestActivity> {

    private static final String TAG = "RecyclerViewTest";

    private boolean mDebug;

    protected RecyclerView mRecyclerView;

    protected AdapterHelper mAdapterHelper;

    Throwable mainThreadException;

    public BaseRecyclerViewInstrumentationTest() {
        this(false);
    }

    public BaseRecyclerViewInstrumentationTest(boolean debug) {
        super("android.support.v7.recyclerview", TestActivity.class);
        mDebug = debug;
    }

    void checkForMainThreadException() throws Throwable {
        if (mainThreadException != null) {
            throw mainThreadException;
        }
    }

    void postExceptionToInstrumentation(Throwable t) {
        if (mDebug) {
            Log.e(TAG, "captured exception on main thread", t);
        }
        mainThreadException = t;
        if (mRecyclerView != null && mRecyclerView
                .getLayoutManager() instanceof TestLayoutManager) {
            TestLayoutManager lm = (TestLayoutManager) mRecyclerView.getLayoutManager();
            // finish all layouts so that we get the correct exception
            while (lm.layoutLatch.getCount() > 0) {
                lm.layoutLatch.countDown();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mRecyclerView != null) {
            try {
                removeRecyclerView();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    public void removeRecyclerView() throws Throwable {
        mRecyclerView = null;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().mContainer.removeAllViews();
            }
        });
    }

    public void setRecyclerView(final RecyclerView recyclerView) throws Throwable {
        mRecyclerView = recyclerView;
        mAdapterHelper = recyclerView.mAdapterHelper;
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().mContainer.addView(recyclerView);
            }
        });
    }

    public void requestLayoutOnUIThread(final View view) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.requestLayout();
            }
        });
    }

    public void scrollBy(final int dt) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRecyclerView.getLayoutManager().canScrollHorizontally()) {
                    mRecyclerView.scrollBy(dt, 0);
                } else {
                    mRecyclerView.scrollBy(0, dt);
                }

            }
        });
    }

    void scrollToPosition(final int position) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecyclerView.getLayoutManager().scrollToPosition(position);
            }
        });
    }

    void smoothScrollToPosition(final int position)
            throws Throwable {
        Log.d(TAG, "SMOOTH scrolling to " + position);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecyclerView.smoothScrollToPosition(position);
            }
        });
        while (mRecyclerView.getLayoutManager().isSmoothScrolling() ||
                mRecyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            if (mDebug) {
                Log.d(TAG, "SMOOTH scrolling step");
            }
            Thread.sleep(200);
        }
        Log.d(TAG, "SMOOTH scrolling done");
    }

    class TestViewHolder extends RecyclerView.ViewHolder {

        Item mBindedItem;

        public TestViewHolder(View itemView) {
            super(itemView);
        }
    }

    class TestLayoutManager extends RecyclerView.LayoutManager {

        CountDownLatch layoutLatch;

        public void expectLayouts(int count) {
            layoutLatch = new CountDownLatch(count);
        }

        public void waitForLayout(long timeout, TimeUnit timeUnit) throws Throwable {
            layoutLatch.await(timeout * (mDebug ? 100 : 1), timeUnit);
            assertEquals("all expected layouts should be executed at the expected time",
                    0, layoutLatch.getCount());
        }

        public void assertLayoutCount(int count, String msg, long timeout) throws Throwable {
            layoutLatch.await(timeout, TimeUnit.SECONDS);
            assertEquals(msg, count, layoutLatch.getCount());
        }

        public void assertNoLayout(String msg, long timeout) throws Throwable {
            layoutLatch.await(timeout, TimeUnit.SECONDS);
            assertFalse(msg, layoutLatch.getCount() == 0);
        }

        public void waitForLayout(long timeout) throws Throwable {
            waitForLayout(timeout * (mDebug ? 10000 : 1), TimeUnit.SECONDS);
        }

        @Override
        public RecyclerView.LayoutParams generateDefaultLayoutParams() {
            return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        void assertVisibleItemPositions() {
            int i = getChildCount();
            TestAdapter testAdapter = (TestAdapter) mRecyclerView.getAdapter();
            while (i-- > 0) {
                View view = getChildAt(i);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                Item item = ((TestViewHolder) lp.mViewHolder).mBindedItem;
                if (mDebug) {
                    Log.d(TAG, "testing item " + i);
                }
                if (!lp.isItemRemoved()) {
                    RecyclerView.ViewHolder vh = mRecyclerView.getChildViewHolder(view);
                    assertSame("item position in LP should match adapter value :" + vh,
                            testAdapter.mItems.get(vh.mPosition), item);
                }
            }
        }

        RecyclerView.LayoutParams getLp(View v) {
            return (RecyclerView.LayoutParams) v.getLayoutParams();
        }

        /**
         * returns skipped (removed) view count.
         */
        int layoutRange(RecyclerView.Recycler recycler, int start,
                int end) {
            assertScrap(recycler);
            int skippedAdd = 0;
            if (mDebug) {
                Log.d(TAG, "will layout items from " + start + " to " + end);
            }
            int diff = end > start ? 1 : -1;
            for (int i = start; i != end; i+=diff) {
                if (mDebug) {
                    Log.d(TAG, "laying out item " + i);
                }
                View view = recycler.getViewForPosition(i);
                assertNotNull("view should not be null for valid position. "
                        + "got null view at position " + i, view);
                if (!getLp(view).isItemRemoved()) {
                    addView(view);
                } else {
                    skippedAdd ++;
                }

                measureChildWithMargins(view, 0, 0);
                layoutDecorated(view, 0, Math.abs(i - start) * 10, getDecoratedMeasuredWidth(view)
                        , getDecoratedMeasuredHeight(view));
            }
            return skippedAdd;
        }

        private void assertScrap(RecyclerView.Recycler recycler) {
            for (RecyclerView.ViewHolder viewHolder : recycler.getScrapList()) {
                assertFalse("Invalid scrap should be no kept", viewHolder.isInvalid());
            }
        }
    }

    static class Item {
        final static AtomicInteger idCounter = new AtomicInteger(0);
        final public int mId = idCounter.incrementAndGet();

        int originalIndex;

        final String text;

        Item(int originalIndex, String text) {
            this.originalIndex = originalIndex;
            this.text = text;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "mId=" + mId +
                    ", originalIndex=" + originalIndex +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    class TestAdapter extends RecyclerView.Adapter<TestViewHolder> {

        List<Item> mItems;

        TestAdapter(int count) {
            mItems = new ArrayList<Item>(count);
            for (int i = 0; i < count; i++) {
                mItems.add(new Item(i, "Item " + i));
            }
        }

        @Override
        public TestViewHolder onCreateViewHolder(ViewGroup parent,
                int viewType) {
            return new TestViewHolder(new TextView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(TestViewHolder holder, int position) {
            final Item item = mItems.get(position);
            ((TextView) (holder.itemView)).setText(item.text);
            holder.mBindedItem = item;
        }

        public void deleteAndNotify(final int start, final int count) throws Throwable {
            deleteAndNotify(new int[]{start, count});
        }

        /**
         * Deletes items in the given ranges.
         * <p>
         * Note that each operation affects the one after so you should offset them properly.
         * <p>
         * For example, if adapter has 5 items (A,B,C,D,E), and then you call this method with
         * <code>[1, 2],[2, 1]</code>, it will first delete items B,C and the new adapter will be
         * A D E. Then it will delete 2,1 which means it will delete E.
         */
        public void deleteAndNotify(final int[]... startCountTuples) throws Throwable {
            for (int[] tuple : startCountTuples) {
                tuple[1] = -tuple[1];
            }
            new AddRemoveRunnable(startCountTuples).runOnMainThread();
        }

        public void addAndNotify(final int start, final int count) throws Throwable {
            addAndNotify(new int[]{start, count});
        }

        public void addAndNotify(final int[]... startCountTuples) throws Throwable {
            new AddRemoveRunnable(startCountTuples).runOnMainThread();
        }

        public void notifyChange() throws Throwable {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }

        public void notifyItemChange(final int start, final int count) throws Throwable {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyItemRangeChanged(start, count);
                }
            });
        }

        /**
         * Similar to other methods but negative count means delete and position count means add.
         * <p>
         * For instance, calling this method with <code>[1,1], [2,-1]</code> it will first add an
         * item to index 1, then remove an item from index 2 (updated index 2)
         */
        public void addDeleteAndNotify(final int[]... startCountTuples) throws Throwable {
            new AddRemoveRunnable(startCountTuples).runOnMainThread();
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }


        private class AddRemoveRunnable implements Runnable {
            final int[][] mStartCountTuples;

            public AddRemoveRunnable(int[][] startCountTuples) {
                mStartCountTuples = startCountTuples;
            }

            public void runOnMainThread() throws Throwable {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    run();
                } else {
                    runTestOnUiThread(this);
                }
            }

            @Override
            public void run() {
                for (int[] tuple : mStartCountTuples) {
                    if (tuple[1] < 0) {
                        delete(tuple);
                    } else {
                        add(tuple);
                    }
                }
            }

            private void add(int[] tuple) {
                for (int i = 0; i < tuple[1]; i++) {
                    mItems.add(tuple[0], new Item(i, "new item " + i));
                }
                // offset others
                for (int i = tuple[0] + tuple[1]; i < mItems.size(); i++) {
                    mItems.get(i).originalIndex += tuple[1];
                }
                notifyItemRangeInserted(tuple[0], tuple[1]);
            }

            private void delete(int[] tuple) {
                for (int i = 0; i < -tuple[1]; i++) {
                    mItems.remove(tuple[0]);
                }
                notifyItemRangeRemoved(tuple[0], -tuple[1]);
            }
        }
    }

    @Override
    public void runTestOnUiThread(Runnable r) throws Throwable {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            super.runTestOnUiThread(r);
        }
    }
}
