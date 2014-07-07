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

import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RecyclerViewLayoutTest extends BaseRecyclerViewInstrumentationTest {

    private static final boolean DEBUG = false;

    public RecyclerViewLayoutTest() {
        super(DEBUG);
    }

    public void testMovingViaStableIds() throws Throwable {
        stableIdsMoveTest(true);
        removeRecyclerView();
        stableIdsMoveTest(false);
        removeRecyclerView();
    }

    public void stableIdsMoveTest(final boolean supportsPredictive) throws Throwable {
        final TestAdapter testAdapter = new TestAdapter(10);
        testAdapter.setHasStableIds(true);
        final AtomicBoolean test = new AtomicBoolean(false);
        final int movedViewFromIndex = 3;
        final int movedViewToIndex = 6;
        final View[] movedView = new View[1];
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                detachAndScrapAttachedViews(recycler);
                try {
                    if (test.get()) {
                        if (state.isPreLayout()) {
                            View view = recycler.getViewForPosition(movedViewFromIndex, true);
                            assertSame("In pre layout, should be able to get moved view w/ old "
                                    + "position", movedView[0], view);
                            RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
                            assertTrue("it should come from scrap", holder.wasReturnedFromScrap());
                            // clear scrap flag
                            holder.clearReturnedFromScrapFlag();
                        } else {
                            View view = recycler.getViewForPosition(movedViewToIndex, true);
                            assertSame("In post layout, should be able to get moved view w/ new "
                                    + "position", movedView[0], view);
                            RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(view);
                            assertTrue("it should come from scrap", holder.wasReturnedFromScrap());
                            // clear scrap flag
                            holder.clearReturnedFromScrapFlag();
                        }
                    }
                    layoutRange(recycler, 0, state.getItemCount());
                } catch (Throwable t) {
                    postExceptionToInstrumentation(t);
                } finally {
                    layoutLatch.countDown();
                }


            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return supportsPredictive;
            }
        };
        RecyclerView recyclerView = new RecyclerView(this.getActivity());
        recyclerView.setAdapter(testAdapter);
        recyclerView.setLayoutManager(lm);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(1);

        movedView[0] = recyclerView.getChildAt(movedViewFromIndex);
        test.set(true);
        lm.expectLayouts(supportsPredictive ? 2 : 1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                Item item = testAdapter.mItems.remove(movedViewFromIndex);
                testAdapter.mItems.add(movedViewToIndex, item);
                testAdapter.notifyItemRemoved(movedViewFromIndex);
                testAdapter.notifyItemInserted(movedViewToIndex);
            }
        });
        lm.waitForLayout(2);
        checkForMainThreadException();
    }
    public void testAdapterChangeDuringLayout() throws Throwable {
        adapterChangeInMainThreadTest("notifyDataSetChanged", new Runnable() {
            @Override
            public void run() {
                mRecyclerView.getAdapter().notifyDataSetChanged();
            }
        });

        adapterChangeInMainThreadTest("notifyItemChanged", new Runnable() {
            @Override
            public void run() {
                mRecyclerView.getAdapter().notifyItemChanged(2);
            }
        });

        adapterChangeInMainThreadTest("notifyItemInserted", new Runnable() {
            @Override
            public void run() {
                mRecyclerView.getAdapter().notifyItemInserted(2);
            }
        });
        adapterChangeInMainThreadTest("notifyItemRemoved", new Runnable() {
            @Override
            public void run() {
                mRecyclerView.getAdapter().notifyItemRemoved(2);
            }
        });
    }

    public void adapterChangeInMainThreadTest(String msg,
            final Runnable onLayoutRunnable) throws Throwable {
        final AtomicBoolean doneFirstLayout = new AtomicBoolean(false);
        TestAdapter testAdapter = new TestAdapter(10);
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                try {
                    layoutRange(recycler, 0, state.getItemCount());
                    if (doneFirstLayout.get()) {
                        onLayoutRunnable.run();
                    }
                } catch (Throwable t) {
                    postExceptionToInstrumentation(t);
                } finally {
                    layoutLatch.countDown();
                }

            }
        };
        RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(testAdapter);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        doneFirstLayout.set(true);
        lm.expectLayouts(1);
        requestLayoutOnUIThread(recyclerView);
        lm.waitForLayout(2);
        removeRecyclerView();
        assertTrue("Invalid data updates should be caught:" + msg,
                mainThreadException instanceof IllegalStateException);
    }

    public void testAdapterChangeDuringScroll() throws Throwable {
        for (int orientation : new int[]{OrientationHelper.HORIZONTAL,
                OrientationHelper.VERTICAL}) {
            adapterChangeDuringScrollTest("notifyDataSetChanged", orientation,
                    new Runnable() {
                        @Override
                        public void run() {
                            mRecyclerView.getAdapter().notifyDataSetChanged();
                        }
                    });
            adapterChangeDuringScrollTest("notifyItemChanged", orientation, new Runnable() {
                @Override
                public void run() {
                    mRecyclerView.getAdapter().notifyItemChanged(2);
                }
            });

            adapterChangeDuringScrollTest("notifyItemInserted", orientation, new Runnable() {
                @Override
                public void run() {
                    mRecyclerView.getAdapter().notifyItemInserted(2);
                }
            });
            adapterChangeDuringScrollTest("notifyItemRemoved", orientation, new Runnable() {
                @Override
                public void run() {
                    mRecyclerView.getAdapter().notifyItemRemoved(2);
                }
            });
        }
    }

    public void adapterChangeDuringScrollTest(String msg, final int orientation,
            final Runnable onScrollRunnable) throws Throwable {
        TestAdapter testAdapter = new TestAdapter(100);
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                try {
                    layoutRange(recycler, 0, 10);
                } catch (Throwable t) {
                    postExceptionToInstrumentation(t);
                } finally {
                    layoutLatch.countDown();
                }
            }

            @Override
            public boolean canScrollVertically() {
                return orientation == OrientationHelper.VERTICAL;
            }

            @Override
            public boolean canScrollHorizontally() {
                return orientation == OrientationHelper.HORIZONTAL;
            }

            public int mockScroll() {
                try {
                    onScrollRunnable.run();
                } catch (Throwable t) {
                    postExceptionToInstrumentation(t);
                } finally {
                    layoutLatch.countDown();
                }
                return 0;
            }

            @Override
            public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,
                    RecyclerView.State state) {
                return mockScroll();
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                    RecyclerView.State state) {
                return mockScroll();
            }
        };
        RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(testAdapter);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        lm.expectLayouts(1);
        scrollBy(200);
        lm.waitForLayout(2);
        removeRecyclerView();
        assertTrue("Invalid data updates should be caught:" + msg,
                mainThreadException instanceof IllegalStateException);
    }

    public void testRecycleOnDetach() throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        final TestAdapter testAdapter = new TestAdapter(10);
        final AtomicBoolean didRunOnDetach = new AtomicBoolean(false);
        final TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                layoutRange(recycler, 0, state.getItemCount() - 1);
                layoutLatch.countDown();
            }

            @Override
            public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
                super.onDetachedFromWindow(view, recycler);
                didRunOnDetach.set(true);
                removeAndRecycleAllViews(recycler);
            }
        };
        recyclerView.setAdapter(testAdapter);
        recyclerView.setLayoutManager(lm);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        removeRecyclerView();
        assertTrue("When recycler view is removed, detach should run", didRunOnDetach.get());
        assertEquals("All children should be recycled", recyclerView.getChildCount(), 0);
    }

    public void testUpdatesWhileDetached() throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        final int initialAdapterSize = 20;
        final TestAdapter adapter = new TestAdapter(initialAdapterSize);
        final AtomicInteger layoutCount = new AtomicInteger(0);
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                layoutRange(recycler, 0, 5);
                layoutCount.incrementAndGet();
                layoutLatch.countDown();
            }
        };
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(lm);
        recyclerView.setHasFixedSize(true);
        lm.expectLayouts(1);
        adapter.addAndNotify(4, 5);
        lm.assertNoLayout("When RV is not attached, layout should not happen", 1);
    }

    public void testUpdatesAfterDetach() throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        final int initialAdapterSize = 20;
        final TestAdapter adapter = new TestAdapter(initialAdapterSize);
        final AtomicInteger layoutCount = new AtomicInteger(0);
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                layoutRange(recycler, 0, 5);
                layoutCount.incrementAndGet();
                layoutLatch.countDown();
            }
        };
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(lm);
        lm.expectLayouts(1);
        recyclerView.setHasFixedSize(true);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        lm.expectLayouts(1);
        final int prevLayoutCount = layoutCount.get();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    adapter.addAndNotify(4, 5);
                    removeRecyclerView();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });

        lm.assertNoLayout("When RV is not attached, layout should not happen", 1);
        assertEquals("No extra layout should happen when detached", prevLayoutCount,
                layoutCount.get());
    }

    public void testNotifyDataSetChangedWithStableIds() throws Throwable {
        final int defaultViewType = 1;
        final Map<Item, Integer> viewTypeMap = new HashMap<Item, Integer>();
        final Map<Integer, Integer> oldPositionToNewPositionMapping =
                new HashMap<Integer, Integer>();
        final TestAdapter adapter = new TestAdapter(100) {
            @Override
            public int getItemViewType(int position) {
                Integer type = viewTypeMap.get(mItems.get(position));
                return type == null ? defaultViewType : type;
            }

            @Override
            public long getItemId(int position) {
                return mItems.get(position).mId;
            }
        };
        adapter.setHasStableIds(true);
        final ArrayList<Item> previousItems = new ArrayList<Item>();
        previousItems.addAll(adapter.mItems);

        final AtomicInteger layoutStart = new AtomicInteger(50);
        final AtomicBoolean validate = new AtomicBoolean(false);
        final int childCount = 10;
        final TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                try {
                    super.onLayoutChildren(recycler, state);
                    if (validate.get()) {
                        assertEquals("Cached views should be kept", 5, recycler
                                .mCachedViews.size());
                        for (RecyclerView.ViewHolder vh : recycler.mCachedViews) {
                            TestViewHolder tvh = (TestViewHolder) vh;
                            assertTrue("view holder should be marked for update",
                                    tvh.needsUpdate());
                            assertTrue("view holder should be marked as invalid", tvh.isInvalid());
                        }
                    }
                    detachAndScrapAttachedViews(recycler);
                    if (validate.get()) {
                        assertEquals("cache size should stay the same", 5,
                                recycler.mCachedViews.size());
                        assertEquals("all views should be scrapped", childCount,
                                recycler.getScrapList().size());
                        for (RecyclerView.ViewHolder vh : recycler.getScrapList()) {
                            // TODO create test case for type change
                            TestViewHolder tvh = (TestViewHolder) vh;
                            assertTrue("view holder should be marked for update",
                                    tvh.needsUpdate());
                            assertTrue("view holder should be marked as invalid", tvh.isInvalid());
                        }
                    }
                    layoutRange(recycler, layoutStart.get(), layoutStart.get() + childCount);
                    if (validate.get()) {
                        for (int i = 0; i < getChildCount(); i++) {
                            View view = getChildAt(i);
                            TestViewHolder tvh = (TestViewHolder) mRecyclerView
                                    .getChildViewHolder(view);
                            final int oldPos = previousItems.indexOf(tvh.mBindedItem);
                            assertEquals("view holder's position should be correct",
                                    oldPositionToNewPositionMapping.get(oldPos).intValue(),
                                    tvh.getPosition());
                            ;
                        }
                    }
                } catch (Throwable t) {
                    postExceptionToInstrumentation(t);
                } finally {
                    layoutLatch.countDown();
                }
            }
        };
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(lm);
        recyclerView.setItemViewCacheSize(10);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        checkForMainThreadException();
        getInstrumentation().waitForIdleSync();
        layoutStart.set(layoutStart.get() + 5);//55
        lm.expectLayouts(1);
        requestLayoutOnUIThread(recyclerView);
        lm.waitForLayout(2);
        validate.set(true);
        lm.expectLayouts(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    adapter.moveItems(false,
                            new int[]{50, 56}, new int[]{51, 1}, new int[]{52, 2},
                            new int[]{53, 54}, new int[]{60, 61}, new int[]{62, 64},
                            new int[]{75, 58});
                    for (int i = 0; i < previousItems.size(); i++) {
                        Item item = previousItems.get(i);
                        oldPositionToNewPositionMapping.put(i, adapter.mItems.indexOf(item));
                    }
                    adapter.notifyChange();
                } catch (Throwable throwable) {
                    postExceptionToInstrumentation(throwable);
                }
            }
        });
        lm.waitForLayout(2);
        checkForMainThreadException();
    }

    public void testFindViewById() throws Throwable {
        findViewByIdTest(false);
        removeRecyclerView();
        findViewByIdTest(true);
    }

    public void findViewByIdTest(final boolean supportPredictive) throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        final int initialAdapterSize = 20;
        final TestAdapter adapter = new TestAdapter(initialAdapterSize);
        final int deleteStart = 6;
        final int deleteCount = 5;
        recyclerView.setAdapter(adapter);
        final AtomicBoolean assertPositions = new AtomicBoolean(false);
        TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                if (assertPositions.get()) {
                    if (state.isPreLayout()) {
                        for (int i = 0; i < deleteStart; i++) {
                            View view = findViewByPosition(i);
                            assertNotNull("find view by position for existing items should work "
                                    + "fine", view);
                            assertFalse("view should not be marked as removed",
                                    ((RecyclerView.LayoutParams) view.getLayoutParams())
                                            .isItemRemoved());
                        }
                        for (int i = 0; i < deleteCount; i++) {
                            View view = findViewByPosition(i + deleteStart);
                            assertNotNull("find view by position should work fine for removed "
                                    + "views in pre-layout", view);
                            assertTrue("view should be marked as removed",
                                    ((RecyclerView.LayoutParams) view.getLayoutParams())
                                            .isItemRemoved());
                        }
                        for (int i = deleteStart + deleteCount; i < 20; i++) {
                            View view = findViewByPosition(i);
                            assertNotNull(view);
                            assertFalse("view should not be marked as removed",
                                    ((RecyclerView.LayoutParams) view.getLayoutParams())
                                            .isItemRemoved());
                        }
                    } else {
                        for (int i = 0; i < initialAdapterSize - deleteCount; i++) {
                            View view = findViewByPosition(i);
                            assertNotNull("find view by position for existing item " + i +
                                    " should work fine. child count:" + getChildCount(), view);
                            TestViewHolder viewHolder =
                                    (TestViewHolder) mRecyclerView.getChildViewHolder(view);
                            assertSame("should be the correct item " + viewHolder
                                    , viewHolder.mBindedItem,
                                    adapter.mItems.get(viewHolder.mPosition));
                            assertFalse("view should not be marked as removed",
                                    ((RecyclerView.LayoutParams) view.getLayoutParams())
                                            .isItemRemoved());
                        }
                    }
                }
                detachAndScrapAttachedViews(recycler);
                layoutRange(recycler, state.getItemCount() - 1, -1);
                layoutLatch.countDown();
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return supportPredictive;
            }
        };
        recyclerView.setLayoutManager(lm);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        getInstrumentation().waitForIdleSync();

        assertPositions.set(true);
        lm.expectLayouts(supportPredictive ? 2 : 1);
        adapter.deleteAndNotify(new int[]{deleteStart, deleteCount - 1}, new int[]{deleteStart, 1});
        lm.waitForLayout(2);
    }

    public void testTypeForCache() throws Throwable {
        final AtomicInteger viewType = new AtomicInteger(1);
        final TestAdapter adapter = new TestAdapter(100) {
            @Override
            public int getItemViewType(int position) {
                return viewType.get();
            }

            @Override
            public long getItemId(int position) {
                return mItems.get(position).mId;
            }
        };
        adapter.setHasStableIds(true);
        final AtomicInteger layoutStart = new AtomicInteger(2);
        final int childCount = 10;
        final TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                detachAndScrapAttachedViews(recycler);
                layoutRange(recycler, layoutStart.get(), layoutStart.get() + childCount);
                layoutLatch.countDown();
            }
        };
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(lm);
        recyclerView.setItemViewCacheSize(10);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        getInstrumentation().waitForIdleSync();
        layoutStart.set(4); // trigger a cache for 3,4
        lm.expectLayouts(1);
        requestLayoutOnUIThread(recyclerView);
        lm.waitForLayout(2);
        //
        viewType.incrementAndGet();
        layoutStart.set(2); // go back to bring views from cache
        lm.expectLayouts(1);
        adapter.mItems.remove(1);
        adapter.notifyChange();
        lm.waitForLayout(2);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 2; i < 4; i++) {
                    RecyclerView.ViewHolder vh = recyclerView.findViewHolderForPosition(2);
                    assertEquals("View holder's type should match latest type", viewType.get(),
                            vh.getItemViewType());
                }
            }
        });
    }

    public void testTypeForExistingViews() throws Throwable {
        final AtomicInteger viewType = new AtomicInteger(1);
        final int invalidatedCount = 2;
        final int layoutStart = 2;
        final TestAdapter adapter = new TestAdapter(100) {
            @Override
            public int getItemViewType(int position) {
                return viewType.get();
            }

            @Override
            public void onBindViewHolder(TestViewHolder holder,
                    int position) {
                super.onBindViewHolder(holder, position);
                if (position >= layoutStart && position < invalidatedCount + layoutStart) {
                    try {
                        assertEquals("holder type should match current view type at position " +
                                position, viewType.get(), holder.getItemViewType());
                    } catch (Throwable t) {
                        postExceptionToInstrumentation(t);
                    }
                }
            }

            @Override
            public long getItemId(int position) {
                return mItems.get(position).mId;
            }
        };
        adapter.setHasStableIds(true);

        final int childCount = 10;
        final TestLayoutManager lm = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                detachAndScrapAttachedViews(recycler);
                layoutRange(recycler, layoutStart, layoutStart + childCount);
                layoutLatch.countDown();
            }
        };
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(lm);
        lm.expectLayouts(1);
        setRecyclerView(recyclerView);
        lm.waitForLayout(2);
        getInstrumentation().waitForIdleSync();
        viewType.incrementAndGet();
        lm.expectLayouts(1);
        adapter.notifyItemChange(layoutStart, invalidatedCount);
        lm.waitForLayout(2);
        checkForMainThreadException();
    }


    public void testState() throws Throwable {
        final TestAdapter adapter = new TestAdapter(10);
        final RecyclerView recyclerView = new RecyclerView(getActivity());
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);
        final AtomicInteger itemCount = new AtomicInteger();
        final AtomicBoolean structureChanged = new AtomicBoolean();
        TestLayoutManager testLayoutManager = new TestLayoutManager() {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                detachAndScrapAttachedViews(recycler);
                layoutRange(recycler, 0, state.getItemCount());
                itemCount.set(state.getItemCount());
                structureChanged.set(state.didStructureChange());
                layoutLatch.countDown();
            }
        };
        recyclerView.setLayoutManager(testLayoutManager);
        testLayoutManager.expectLayouts(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().mContainer.addView(recyclerView);
            }
        });
        testLayoutManager.waitForLayout(2, TimeUnit.SECONDS);

        assertEquals("item count in state should be correct", adapter.getItemCount()
                , itemCount.get());
        assertEquals("structure changed should be true for first layout", true,
                structureChanged.get());
        Thread.sleep(1000); //wait for other layouts.
        testLayoutManager.expectLayouts(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerView.requestLayout();
            }
        });
        testLayoutManager.waitForLayout(2);
        assertEquals("in second layout,structure changed should be false", false,
                structureChanged.get());
        testLayoutManager.expectLayouts(1); //
        adapter.deleteAndNotify(3, 2);
        testLayoutManager.waitForLayout(2);
        assertEquals("when items are removed, item count in state should be updated",
                adapter.getItemCount(),
                itemCount.get());
        assertEquals("structure changed should be true when items are removed", true,
                structureChanged.get());
        testLayoutManager.expectLayouts(1);
        adapter.addAndNotify(2, 5);
        testLayoutManager.waitForLayout(2);

        assertEquals("when items are added, item count in state should be updated",
                adapter.getItemCount(),
                itemCount.get());
        assertEquals("structure changed should be true when items are removed", true,
                structureChanged.get());

    }

}
