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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RecyclerViewAnimationsTest extends BaseRecyclerViewInstrumentationTest {

    private static final boolean DEBUG = false;

    private static final String TAG = "RecyclerViewAnimationsTest";

    AnimationLayoutManager mLayoutManager;

    TestAdapter mTestAdapter;

    public RecyclerViewAnimationsTest() {
        super(DEBUG);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    RecyclerView setupBasic(int itemCount) throws Throwable {
        return setupBasic(itemCount, 0, itemCount);
    }

    RecyclerView setupBasic(int itemCount, int firstLayoutStartIndex, int firstLayoutItemCount)
            throws Throwable {
        return setupBasic(itemCount, firstLayoutStartIndex, firstLayoutItemCount, null);
    }

    RecyclerView setupBasic(int itemCount, int firstLayoutStartIndex, int firstLayoutItemCount,
            TestAdapter testAdapter)
            throws Throwable {
        final TestRecyclerView recyclerView = new TestRecyclerView(getActivity());
        recyclerView.setHasFixedSize(true);
        if (testAdapter == null) {
            mTestAdapter = new TestAdapter(itemCount);
        } else {
            mTestAdapter = testAdapter;
        }
        recyclerView.setAdapter(mTestAdapter);
        mLayoutManager = new AnimationLayoutManager();
        recyclerView.setLayoutManager(mLayoutManager);
        mLayoutManager.mOnLayoutCallbacks.mLayoutMin = firstLayoutStartIndex;
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = firstLayoutItemCount;

        mLayoutManager.expectLayouts(1);
        recyclerView.expectDraw(1);
        setRecyclerView(recyclerView);
        mLayoutManager.waitForLayout(2);
        recyclerView.waitForDraw(1);
        mLayoutManager.mOnLayoutCallbacks.reset();
        getInstrumentation().waitForIdleSync();
        assertEquals("extra layouts should not happend", 1, mLayoutManager.getTotalLayoutCount());
        assertEquals("all expected children should be laid out", firstLayoutItemCount,
                mLayoutManager.getChildCount());
        return recyclerView;
    }


    public void testGetItemForDeletedView() throws Throwable {
        getItemForDeletedViewTest(false);
        getItemForDeletedViewTest(true);
    }

    public void getItemForDeletedViewTest(boolean stableIds) throws Throwable {
        final Set<Integer> itemViewTypeQueries = new HashSet<Integer>();
        final Set<Integer> itemIdQueries = new HashSet<Integer>();
        TestAdapter adapter = new TestAdapter(10) {
            @Override
            public int getItemViewType(int position) {
                itemViewTypeQueries.add(position);
                return super.getItemViewType(position);
            }

            @Override
            public long getItemId(int position) {
                itemIdQueries.add(position);
                return mItems.get(position).mId;
            }
        };
        adapter.setHasStableIds(stableIds);
        setupBasic(10, 0, 10, adapter);
        assertEquals("getItemViewType for all items should be called", 10,
                itemViewTypeQueries.size());
        if (adapter.hasStableIds()) {
            assertEquals("getItemId should be called when adapter has stable ids", 10,
                    itemIdQueries.size());
        } else {
            assertEquals("getItemId should not be called when adapter does not have stable ids", 0,
                    itemIdQueries.size());
        }
        itemViewTypeQueries.clear();
        itemIdQueries.clear();
        mLayoutManager.expectLayouts(2);
        // delete last two
        final int deleteStart = 8;
        final int deleteCount = adapter.getItemCount() - deleteStart;
        adapter.deleteAndNotify(deleteStart, deleteCount);
        mLayoutManager.waitForLayout(2);
        for (int i = 0; i < deleteStart; i++) {
            assertTrue("getItemViewType for existing item " + i + " should be called",
                    itemViewTypeQueries.contains(i));
            if (adapter.hasStableIds()) {
                assertTrue("getItemId for existing item " + i
                        + " should be called when adapter has stable ids",
                        itemIdQueries.contains(i));
            }
        }
        for (int i = deleteStart; i < deleteStart + deleteCount; i++) {
            assertFalse("getItemViewType for deleted item " + i + " SHOULD NOT be called",
                    itemViewTypeQueries.contains(i));
            if (adapter.hasStableIds()) {
                assertFalse("getItemId for deleted item " + i + " SHOULD NOT be called",
                        itemIdQueries.contains(i));
            }
        }
    }

    public void testDeleteInvisibleMultiStep() throws Throwable {
        setupBasic(1000, 1, 7);
        mLayoutManager.mOnLayoutCallbacks.mLayoutMin = 1;
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = 7;
        mLayoutManager.expectLayouts(1);
        // try to trigger race conditions
        int targetItemCount = mTestAdapter.getItemCount();
        for (int i = 0; i < 100; i++) {
            mTestAdapter.deleteAndNotify(new int[]{0, 1}, new int[]{7, 1});
            targetItemCount -= 2;
        }
        // wait until main thread runnables are consumed
        while (targetItemCount != mTestAdapter.getItemCount()) {
            Thread.sleep(100);
        }
        mLayoutManager.waitForLayout(2);
    }

    public void testAddManyMultiStep() throws Throwable {
        setupBasic(10, 1, 7);
        mLayoutManager.mOnLayoutCallbacks.mLayoutMin = 1;
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = 7;
        mLayoutManager.expectLayouts(1);
        // try to trigger race conditions
        int targetItemCount = mTestAdapter.getItemCount();
        for (int i = 0; i < 100; i++) {
            mTestAdapter.addAndNotify(0, 1);
            mTestAdapter.addAndNotify(7, 1);
            targetItemCount += 2;
        }
        // wait until main thread runnables are consumed
        while (targetItemCount != mTestAdapter.getItemCount()) {
            Thread.sleep(100);
        }
        mLayoutManager.waitForLayout(2);
    }

    public void testBasicDelete() throws Throwable {
        setupBasic(10);
        final OnLayoutCallbacks callbacks = new OnLayoutCallbacks() {
            @Override
            public void postDispatchLayout() {
                // verify this only in first layout
                assertEquals("deleted views should still be children of RV",
                        mLayoutManager.getChildCount() + mDeletedViewCount
                        , mRecyclerView.getChildCount());
            }

            @Override
            void afterPreLayout(RecyclerView.Recycler recycler,
                    AnimationLayoutManager layoutManager,
                    RecyclerView.State state) {
                super.afterPreLayout(recycler, layoutManager, state);
                mLayoutItemCount = 3;
                mLayoutMin = 0;
            }
        };
        callbacks.mLayoutItemCount = 10;
        callbacks.setExpectedItemCounts(10, 3);
        mLayoutManager.setOnLayoutCallbacks(callbacks);

        mLayoutManager.expectLayouts(2);
        mTestAdapter.deleteAndNotify(0, 7);
        mLayoutManager.waitForLayout(2);
        callbacks.reset();// when animations end another layout will happen
    }


    public void testAdapterChangeDuringScrolling() throws Throwable {
        setupBasic(10);
        final AtomicInteger onLayoutItemCount = new AtomicInteger(0);
        final AtomicInteger onScrollItemCount = new AtomicInteger(0);

        mLayoutManager.setOnLayoutCallbacks(new OnLayoutCallbacks() {
            @Override
            void onLayoutChildren(RecyclerView.Recycler recycler,
                    AnimationLayoutManager lm, RecyclerView.State state) {
                onLayoutItemCount.set(state.getItemCount());
                super.onLayoutChildren(recycler, lm, state);
            }

            @Override
            public void onScroll(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
                onScrollItemCount.set(state.getItemCount());
                super.onScroll(dx, recycler, state);
            }
        });
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTestAdapter.mItems.remove(5);
                mTestAdapter.notifyItemRangeRemoved(5, 1);
                mRecyclerView.scrollBy(0, 100);
                assertTrue("scrolling while there are pending adapter updates should "
                        + "trigger a layout", mLayoutManager.mOnLayoutCallbacks.mLayoutCount > 0);
                assertEquals("scroll by should be called w/ updated adapter count",
                        mTestAdapter.mItems.size(), onScrollItemCount.get());

            }
        });
    }

    public void testAddInvisibleAndVisible() throws Throwable {
        setupBasic(10, 1, 7);
        mLayoutManager.expectLayouts(2);
        mLayoutManager.mOnLayoutCallbacks.setExpectedItemCounts(10, 12);
        mTestAdapter.addAndNotify(0, 1);// add a new item 0 // invisible
        mTestAdapter.addAndNotify(7, 1);// add a new item after 5th (old 5, new 6)
        mLayoutManager.waitForLayout(2);
    }

    public void testAddInvisible() throws Throwable {
        setupBasic(10, 1, 7);
        mLayoutManager.expectLayouts(1);
        mLayoutManager.mOnLayoutCallbacks.setExpectedItemCounts(10, 12);
        mTestAdapter.addAndNotify(0, 1);// add a new item 0
        mTestAdapter.addAndNotify(8, 1);// add a new item after 6th (old 6, new 7)
        mLayoutManager.waitForLayout(2);
    }

    public void testBasicAdd() throws Throwable {
        setupBasic(10);
        mLayoutManager.expectLayouts(2);
        setExpectedItemCounts(10, 13);
        mTestAdapter.addAndNotify(2, 3);
        mLayoutManager.waitForLayout(2);
    }

    public TestRecyclerView getTestRecyclerView() {
        return (TestRecyclerView) mRecyclerView;
    }

    public void testRemoveScrapInvalidate() throws Throwable {
        setupBasic(10);
        TestRecyclerView testRecyclerView = getTestRecyclerView();
        mLayoutManager.expectLayouts(1);
        testRecyclerView.expectDraw(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTestAdapter.mItems.clear();
                mTestAdapter.notifyDataSetChanged();
            }
        });
        mLayoutManager.waitForLayout(2);
        testRecyclerView.waitForDraw(2);
    }

    public void testDeleteVisibleAndInvisible() throws Throwable {
        setupBasic(11, 3, 5); //layout items  3 4 5 6 7
        mLayoutManager.expectLayouts(2);
        setLayoutRange(3, 5); //layout previously invisible child 10 from end of the list
        setExpectedItemCounts(9, 8);
        mTestAdapter.deleteAndNotify(new int[]{4, 1}, new int[]{7, 2});// delete items 4, 8, 9
        mLayoutManager.waitForLayout(2);
    }

    public void testFindPositionOffset() throws Throwable {
        setupBasic(10);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // [0,1,2,3,4]
                // delete 1
                mTestAdapter.notifyItemRangeRemoved(1, 1);
                // delete 3
                mTestAdapter.notifyItemRangeRemoved(2, 1);
                mAdapterHelper.preProcess();
                // [0,2,4]
                assertEquals("offset check", 0, mAdapterHelper.findPositionOffset(0));
                assertEquals("offset check", 1, mAdapterHelper.findPositionOffset(2));
                assertEquals("offset check", 2, mAdapterHelper.findPositionOffset(4));

            }
        });
    }

    private void setLayoutRange(int start, int count) {
        mLayoutManager.mOnLayoutCallbacks.mLayoutMin = start;
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = count;
    }

    private void setExpectedItemCounts(int preLayout, int postLayout) {
        mLayoutManager.mOnLayoutCallbacks.setExpectedItemCounts(preLayout, postLayout);
    }

    public void testDeleteInvisible() throws Throwable {
        setupBasic(10, 1, 7);
        mLayoutManager.mOnLayoutCallbacks.mLayoutMin = 1;
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = 7;
        mLayoutManager.expectLayouts(1);
        mLayoutManager.mOnLayoutCallbacks.setExpectedItemCounts(8, 8);
        mTestAdapter.deleteAndNotify(new int[]{0, 1}, new int[]{7, 1});// delete item id 0,8
        mLayoutManager.waitForLayout(2);
    }

    private CollectPositionResult findByPos(RecyclerView.Recycler recycler,
            RecyclerView.State state, int position) {
        RecyclerView.ViewHolder scrap = recycler
                .getScrapViewForPosition(position, RecyclerView.INVALID_TYPE, true);
        if (scrap != null) {
            return CollectPositionResult.fromScrap(scrap);
        }
        return CollectPositionResult.fromAdapter(
                mRecyclerView.getChildViewHolder(recycler.getViewForPosition(position)));
    }

    public Map<Integer, CollectPositionResult> collectPositions(RecyclerView.Recycler recycler,
            RecyclerView.State state, int... positions) {
        Map<Integer, CollectPositionResult> positionToAdapterMapping
                = new HashMap<Integer, CollectPositionResult>();
        for (int position : positions) {
            if (position < 0) {
                continue;
            }
            positionToAdapterMapping.put(position, findByPos(recycler, state, position));
        }
        return positionToAdapterMapping;
    }

    public void testAddDelete2() throws Throwable {
        positionStatesTest(5, 0, 5, new AdapterOps() {
            // 0 1 2 3 4
            // 0 1 2 a b 3 4
            // 0 1 b 3 4
            // pre: 0 1 2 3 4
            // pre w/ adap: 0 1 2 b 3 4
            @Override
            void onRun(TestAdapter adapter) throws Throwable {
                adapter.addDeleteAndNotify(new int[]{3, 2}, new int[]{2, -2});
            }
        }, PositionConstraint.scrap(2, 2, -1), PositionConstraint.scrap(1, 1, 1),
                PositionConstraint.scrap(3, 3, 3)
        );
    }

    public void testAddDelete1() throws Throwable {
        positionStatesTest(5, 0, 5, new AdapterOps() {
            // 0 1 2 3 4
            // 0 1 2 a b 3 4
            // 0 2 a b 3 4
            // 0 c d 2 a b 3 4
            // 0 c d 2 a 4
            // c d 2 a 4
            // pre: 0 1 2 3 4
            @Override
            void onRun(TestAdapter adapter) throws Throwable {
                adapter.addDeleteAndNotify(new int[]{3, 2}, new int[]{1, -1},
                        new int[]{1, 2}, new int[]{5, -2}, new int[]{0, -1});
            }
        }, PositionConstraint.scrap(0, 0, -1), PositionConstraint.scrap(1, 1, -1),
                PositionConstraint.scrap(2, 2, 2), PositionConstraint.scrap(3, 3, -1),
                PositionConstraint.scrap(4, 4, 4), PositionConstraint.adapter(0),
                PositionConstraint.adapter(1), PositionConstraint.adapter(3)
        );
    }

    public void testAddSameIndexTwice() throws Throwable {
        positionStatesTest(12, 2, 7, new AdapterOps() {
            @Override
            void onRun(TestAdapter adapter) throws Throwable {
                adapter.addAndNotify(new int[]{1, 2}, new int[]{5, 1}, new int[]{5, 1},
                        new int[]{11, 1});
            }
        }, PositionConstraint.adapterScrap(0, 0), PositionConstraint.adapterScrap(1, 3),
                PositionConstraint.scrap(2, 2, 4), PositionConstraint.scrap(3, 3, 7),
                PositionConstraint.scrap(4, 4, 8), PositionConstraint.scrap(7, 7, 12),
                PositionConstraint.scrap(8, 8, 13)
        );
    }

    public void testDeleteTwice() throws Throwable {
        positionStatesTest(12, 2, 7, new AdapterOps() {
            @Override
            void onRun(TestAdapter adapter) throws Throwable {
                adapter.deleteAndNotify(new int[]{0, 1}, new int[]{1, 1}, new int[]{7, 1},
                        new int[]{0, 1});// delete item ids 0,2,9,1
            }
        }, PositionConstraint.scrap(2, 0, -1), PositionConstraint.scrap(3, 1, 0),
                PositionConstraint.scrap(4, 2, 1), PositionConstraint.scrap(5, 3, 2),
                PositionConstraint.scrap(6, 4, 3), PositionConstraint.scrap(8, 6, 5),
                PositionConstraint.adapterScrap(7, 6), PositionConstraint.adapterScrap(8, 7)
        );
    }


    public void positionStatesTest(int itemCount, int firstLayoutStartIndex,
            int firstLayoutItemCount
            , AdapterOps adapterChanges, final PositionConstraint... constraints) throws Throwable {
        setupBasic(itemCount, firstLayoutStartIndex, firstLayoutItemCount);
        mLayoutManager.expectLayouts(2);
        mLayoutManager.mOnLayoutCallbacks = new OnLayoutCallbacks() {
            @Override
            void beforePreLayout(RecyclerView.Recycler recycler, AnimationLayoutManager lm,
                    RecyclerView.State state) {
                super.beforePreLayout(recycler, lm, state);
                //harmless
                lm.detachAndScrapAttachedViews(recycler);
                final int[] ids = new int[constraints.length];
                for (int i = 0; i < constraints.length; i++) {
                    ids[i] = constraints[i].mPreLayoutPos;
                }
                Map<Integer, CollectPositionResult> positions
                        = collectPositions(recycler, state, ids);
                for (PositionConstraint constraint : constraints) {
                    if (constraint.mPreLayoutPos != -1) {
                        constraint.validate(state, positions.get(constraint.mPreLayoutPos),
                                lm.getLog());
                    }
                }
            }

            @Override
            void beforePostLayout(RecyclerView.Recycler recycler, AnimationLayoutManager lm,
                    RecyclerView.State state) {
                super.beforePostLayout(recycler, lm, state);
                lm.detachAndScrapAttachedViews(recycler);
                final int[] ids = new int[constraints.length];
                for (int i = 0; i < constraints.length; i++) {
                    ids[i] = constraints[i].mPostLayoutPos;
                }
                Map<Integer, CollectPositionResult> positions
                        = collectPositions(recycler, state, ids);
                for (PositionConstraint constraint : constraints) {
                    if (constraint.mPostLayoutPos >= 0) {
                        constraint.validate(state, positions.get(constraint.mPostLayoutPos),
                                lm.getLog());
                    }
                }
            }
        };
        adapterChanges.run(mTestAdapter);
        mLayoutManager.waitForLayout(2);
        for (PositionConstraint constraint : constraints) {
            constraint.assertValidate();
        }
    }

    class AnimationLayoutManager extends TestLayoutManager {

        private int mTotalLayoutCount = 0;
        private String log;

        OnLayoutCallbacks mOnLayoutCallbacks = new OnLayoutCallbacks() {
        };



        @Override
        public boolean supportsPredictiveItemAnimations() {
            return true;
        }

        public String getLog() {
            return log;
        }

        private String prepareLog(RecyclerView.Recycler recycler, RecyclerView.State state) {
            StringBuilder builder = new StringBuilder();
            builder.append("is pre layout:").append(state.isPreLayout());
            builder.append("ViewHolders:\n");
            for (RecyclerView.ViewHolder vh : ((TestRecyclerView)mRecyclerView).collectViewHolders()) {
                builder.append(vh).append("\n");
            }
            builder.append("scrap:\n");
            for (RecyclerView.ViewHolder vh : recycler.getScrapList()) {
                builder.append(vh).append("\n");
            }
            if (state.isPreLayout()) {
                log = "\n" + builder.toString();
            } else {
                log += "\n" + builder.toString();
            }
            return log;
        }

        @Override
        public void expectLayouts(int count) {
            super.expectLayouts(count);
            mOnLayoutCallbacks.mLayoutCount = 0;
        }

        public void setOnLayoutCallbacks(OnLayoutCallbacks onLayoutCallbacks) {
            mOnLayoutCallbacks = onLayoutCallbacks;
        }

        @Override
        public final void onLayoutChildren(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            try {
                mTotalLayoutCount++;
                prepareLog(recycler, state);
                mOnLayoutCallbacks.onLayoutChildren(recycler, this, state);
            } finally {
                layoutLatch.countDown();
            }
        }

        public int getTotalLayoutCount() {
            return mTotalLayoutCount;
        }

        @Override
        public boolean canScrollVertically() {
            return true;
        }

        @Override
        public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            mOnLayoutCallbacks.onScroll(dy, recycler, state);
            return super.scrollVerticallyBy(dy, recycler, state);
        }

        public void onPostDispatchLayout() {
            mOnLayoutCallbacks.postDispatchLayout();
        }

        @Override
        public void waitForLayout(long timeout, TimeUnit timeUnit) throws Throwable {
            super.waitForLayout(timeout, timeUnit);
            checkForMainThreadException();
        }
    }

    abstract class OnLayoutCallbacks {

        int mLayoutMin = Integer.MIN_VALUE;

        int mLayoutItemCount = Integer.MAX_VALUE;

        int expectedPreLayoutItemCount = -1;

        int expectedPostLayoutItemCount = -1;

        int mDeletedViewCount;

        int mLayoutCount = 0;

        void setExpectedItemCounts(int preLayout, int postLayout) {
            expectedPreLayoutItemCount = preLayout;
            expectedPostLayoutItemCount = postLayout;
        }

        void reset() {
            mLayoutMin = Integer.MIN_VALUE;
            mLayoutItemCount = Integer.MAX_VALUE;
            expectedPreLayoutItemCount = -1;
            expectedPostLayoutItemCount = -1;
            mLayoutCount = 0;
        }

        void beforePreLayout(RecyclerView.Recycler recycler,
                AnimationLayoutManager lm, RecyclerView.State state) {
            mDeletedViewCount = 0;
            for (int i = 0; i < lm.getChildCount(); i++) {
                View v = lm.getChildAt(i);
                if (lm.getLp(v).isItemRemoved()) {
                    mDeletedViewCount++;
                }
            }
        }

        void doLayout(RecyclerView.Recycler recycler, AnimationLayoutManager lm,
                RecyclerView.State state) {
            if (DEBUG) {
                Log.d(TAG, "item count " + state.getItemCount());
            }
            lm.detachAndScrapAttachedViews(recycler);
            final int start = mLayoutMin == Integer.MIN_VALUE ? 0 : mLayoutMin;
            final int count = mLayoutItemCount
                    == Integer.MAX_VALUE ? state.getItemCount() : mLayoutItemCount;
            int skippedAdd = lm.layoutRange(recycler, start, start + count);
            assertEquals("correct # of children should be laid out",
                    count - skippedAdd, lm.getChildCount());
            lm.assertVisibleItemPositions();
        }

        void onLayoutChildren(RecyclerView.Recycler recycler, AnimationLayoutManager lm,
                RecyclerView.State state) {

            if (state.isPreLayout()) {
                if (expectedPreLayoutItemCount != -1) {
                    assertEquals("on pre layout, state should return abstracted adapter size",
                            expectedPreLayoutItemCount, state.getItemCount());
                }
                beforePreLayout(recycler, lm, state);
            } else {
                if (expectedPostLayoutItemCount != -1) {
                    assertEquals("on post layout, state should return real adapter size",
                            expectedPostLayoutItemCount, state.getItemCount());
                }
                beforePostLayout(recycler, lm, state);
            }
            doLayout(recycler, lm, state);
            if (state.isPreLayout()) {
                afterPreLayout(recycler, lm, state);
            } else {
                afterPostLayout(recycler, lm, state);
            }
            mLayoutCount++;
        }

        void afterPreLayout(RecyclerView.Recycler recycler, AnimationLayoutManager layoutManager,
                RecyclerView.State state) {
        }

        void beforePostLayout(RecyclerView.Recycler recycler, AnimationLayoutManager layoutManager,
                RecyclerView.State state) {
        }

        void afterPostLayout(RecyclerView.Recycler recycler, AnimationLayoutManager layoutManager,
                RecyclerView.State state) {
        }

        void postDispatchLayout() {
        }

        public void onScroll(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {

        }
    }

    class TestRecyclerView extends RecyclerView {

        CountDownLatch drawLatch;

        public TestRecyclerView(Context context) {
            super(context);
        }

        public TestRecyclerView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TestRecyclerView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        void initAdapterManager() {
            super.initAdapterManager();
            mAdapterHelper.mOnItemProcessedCallback = new Runnable() {
                @Override
                public void run() {
                    validatePostUpdateOp();
                }
            };
        }

        public void expectDraw(int count) {
            drawLatch = new CountDownLatch(count);
        }

        public void waitForDraw(long timeout) throws Throwable {
            drawLatch.await(timeout * (DEBUG ? 100 : 1), TimeUnit.SECONDS);
            assertEquals("all expected draws should happen at the expected time frame",
                    0, drawLatch.getCount());
        }

        List<ViewHolder> collectViewHolders() {
            List<ViewHolder> holders = new ArrayList<ViewHolder>();
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                ViewHolder holder = getChildViewHolderInt(getChildAt(i));
                if (holder != null) {
                    holders.add(holder);
                }
            }
            return holders;
        }


        private void validateViewHolderPositions() {
            final Set<Integer> removedOffsets = new HashSet<Integer>();
            final Set<Integer> existingOffsets = new HashSet<Integer>();
            int childCount = getLayoutManager().getChildCount();
            for (int i = 0; i < childCount; i++) {
                ViewHolder vh = getChildViewHolderInt(getChildAt(i));
                if (vh.isRemoved()) {
                    if (!removedOffsets.add(vh.getPosition())) {
                        throw new IllegalStateException(
                                "view holder position conflict for deleted views " + vh
                                        .getPosition());
                    }
                } else {
                    if (!existingOffsets.add(vh.getPosition())) {
                        throw new IllegalStateException(
                                "view holder position conflict for existing views " + vh
                                        .getPosition());
                    }
                }
            }
        }

        void validatePostUpdateOp() {
            try {
                validateViewHolderPositions();
                if (super.mState.isPreLayout()) {
                    validatePreLayoutSequence((AnimationLayoutManager) getLayoutManager());
                }
                validateAdapterPosition((AnimationLayoutManager) getLayoutManager());
            } catch (Throwable t) {
                postExceptionToInstrumentation(t);
            }
        }



        private void validateAdapterPosition(AnimationLayoutManager lm) {
            for (ViewHolder vh : collectViewHolders()) {
                if (!vh.isRemoved() && vh.mPreLayoutPosition >= 0) {
                    assertEquals("adapter position calculations should match view holder "
                            + "positions\n" + vh + "\n" + lm.getLog(),
                            mAdapterHelper.findPositionOffset(vh.mPreLayoutPosition), vh.mPosition);
                }
            }
        }

        // ensures pre layout positions are continuous block. This is not necessarily a case
        // but valid in test RV
        private void validatePreLayoutSequence(AnimationLayoutManager lm) {
            Set<Integer> preLayoutPositions = new HashSet<Integer>();
            for (ViewHolder vh : collectViewHolders()) {
                assertTrue("pre layout positions should be distinct " + lm.getLog(),
                        preLayoutPositions.add(vh.mPreLayoutPosition));
            }
            int minPos = Integer.MAX_VALUE;
            for (Integer pos : preLayoutPositions) {
                if (pos < minPos) {
                    minPos = pos;
                }
            }
            for (int i = 1; i < preLayoutPositions.size(); i++) {
                assertNotNull("next position should exist " + lm.getLog(),
                        preLayoutPositions.contains(minPos + i));
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (drawLatch != null) {
                drawLatch.countDown();
            }
        }

        @Override
        void dispatchLayout() {
            try {
                super.dispatchLayout();
                if (getLayoutManager() instanceof AnimationLayoutManager) {
                    ((AnimationLayoutManager) getLayoutManager()).onPostDispatchLayout();
                }
            } catch (Throwable t) {
                postExceptionToInstrumentation(t);
            }

        }


    }

    abstract class AdapterOps {

        final public void run(TestAdapter adapter) throws Throwable {
            onRun(adapter);
        }

        abstract void onRun(TestAdapter testAdapter) throws Throwable;
    }

    static class CollectPositionResult {

        // true if found in scrap
        public RecyclerView.ViewHolder scrapResult;

        public RecyclerView.ViewHolder adapterResult;

        static CollectPositionResult fromScrap(RecyclerView.ViewHolder viewHolder) {
            CollectPositionResult cpr = new CollectPositionResult();
            cpr.scrapResult = viewHolder;
            return cpr;
        }

        static CollectPositionResult fromAdapter(RecyclerView.ViewHolder viewHolder) {
            CollectPositionResult cpr = new CollectPositionResult();
            cpr.adapterResult = viewHolder;
            return cpr;
        }
    }

    static class PositionConstraint {

        public static enum Type {
            scrap,
            adapter,
            adapterScrap /*first pass adapter, second pass scrap*/
        }

        Type mType;

        int mOldPos; // if VH

        int mPreLayoutPos;

        int mPostLayoutPos;

        int mValidateCount = 0;

        public static PositionConstraint scrap(int oldPos, int preLayoutPos, int postLayoutPos) {
            PositionConstraint constraint = new PositionConstraint();
            constraint.mType = Type.scrap;
            constraint.mOldPos = oldPos;
            constraint.mPreLayoutPos = preLayoutPos;
            constraint.mPostLayoutPos = postLayoutPos;
            return constraint;
        }

        public static PositionConstraint adapterScrap(int preLayoutPos, int position) {
            PositionConstraint constraint = new PositionConstraint();
            constraint.mType = Type.adapterScrap;
            constraint.mOldPos = RecyclerView.NO_POSITION;
            constraint.mPreLayoutPos = preLayoutPos;
            constraint.mPostLayoutPos = position;// adapter pos does not change
            return constraint;
        }

        public static PositionConstraint adapter(int position) {
            PositionConstraint constraint = new PositionConstraint();
            constraint.mType = Type.adapter;
            constraint.mPreLayoutPos = RecyclerView.NO_POSITION;
            constraint.mOldPos = RecyclerView.NO_POSITION;
            constraint.mPostLayoutPos = position;// adapter pos does not change
            return constraint;
        }

        public void assertValidate() {
            int expectedValidate = 0;
            if (mPreLayoutPos >= 0) {
                expectedValidate ++;
            }
            if (mPostLayoutPos >= 0) {
                expectedValidate ++;
            }
            assertEquals("should run all validates", expectedValidate, mValidateCount);
        }

        @Override
        public String toString() {
            return "Cons{" +
                    "t=" + mType.name() +
                    ", old=" + mOldPos +
                    ", pre=" + mPreLayoutPos +
                    ", post=" + mPostLayoutPos +
                    '}';
        }

        public void validate(RecyclerView.State state, CollectPositionResult result, String log) {
            mValidateCount ++;
            assertNotNull(this + ": result should not be null\n" + log, result);
            RecyclerView.ViewHolder viewHolder;
            if (mType == Type.scrap || (mType == Type.adapterScrap && !state.isPreLayout())) {
                assertNotNull(this + ": result should come from scrap\n" + log, result.scrapResult);
                viewHolder = result.scrapResult;
            } else {
                assertNotNull(this + ": result should come from adapter\n"  + log,
                        result.adapterResult);
                assertEquals(this + ": old position should be none when it came from adapter\n" + log,
                        RecyclerView.NO_POSITION, result.adapterResult.getOldPosition());
                viewHolder = result.adapterResult;
            }
            if (state.isPreLayout()) {
                assertEquals(this + ": pre-layout position should match\n" + log, mPreLayoutPos,
                        viewHolder.mPreLayoutPosition);
                assertEquals(this + ": pre-layout getPosition should match\n" + log, mPreLayoutPos,
                        viewHolder.getPosition());
                if (mType == Type.scrap) {
                    assertEquals(this + ": old position should match\n" + log, mOldPos,
                            result.scrapResult.getOldPosition());
                }
            } else if (mType == Type.adapter || mType == Type.adapterScrap || !result.scrapResult
                    .isRemoved()) {
                assertEquals(this + ": post-layout position should match\n" + log, mPostLayoutPos,
                        viewHolder.getPosition());
            }
        }
    }
}
