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
package android.support.v7.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static android.support.v7.widget.RecyclerView.ItemAnimator.FLAG_CHANGED;
import static android.support.v7.widget.RecyclerView.ItemAnimator.FLAG_MOVED;
import static android.support.v7.widget.RecyclerView.ItemAnimator.FLAG_REMOVED;

/**
 * Includes tests for the new RecyclerView animations API (v2).
 */
public class ItemAnimatorV2ApiTest extends BaseRecyclerViewAnimationsTest {

    @Override
    protected RecyclerView.ItemAnimator createItemAnimator() {
        return mAnimator;
    }

    public void testSimpleAdd() throws Throwable {
        setupBasic(10);
        mLayoutManager.expectLayouts(2);
        mTestAdapter.addAndNotify(2, 1);
        mLayoutManager.waitForLayout(2);
        RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);
        assertEquals(1, mAnimator.animateAppearanceList.size());
        AnimateAppearance log = mAnimator.animateAppearanceList.get(0);
        assertSame(vh, log.viewHolder);
        assertNull(log.preInfo);
        assertEquals(0, log.postInfo.changeFlags);
        // the first two should not receive anything
        for (int i = 0; i < 2; i++) {
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        for (int i = 3; i < mTestAdapter.getItemCount(); i++) {
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(FLAG_MOVED, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testSimpleRemove() throws Throwable {
        setupBasic(10);
        RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);
        mLayoutManager.expectLayouts(2);
        mTestAdapter.deleteAndNotify(2, 1);
        mLayoutManager.waitForLayout(2);
        checkForMainThreadException();
        assertEquals(1, mAnimator.animateDisappearanceList.size());
        AnimateDisappearance log = mAnimator.animateDisappearanceList.get(0);
        assertSame(vh, log.viewHolder);
        assertFalse(mAnimator.postLayoutInfo.containsKey(vh));
        assertEquals(FLAG_REMOVED, log.preInfo.changeFlags);
        // the first two should not receive anything
        for (int i = 0; i < 2; i++) {
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        for (int i = 3; i < mTestAdapter.getItemCount(); i++) {
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(FLAG_MOVED, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testSimpleUpdate() throws Throwable {
        setupBasic(10);
        RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);
        mLayoutManager.expectLayouts(2);
        mTestAdapter.changeAndNotify(2, 1);
        mLayoutManager.waitForLayout(2);
        assertEquals(1, mAnimator.animateChangeList.size());
        AnimateChange log = mAnimator.animateChangeList.get(0);
        assertSame(vh, log.viewHolder);
        assertSame(vh, log.newHolder);
        assertTrue(mAnimator.preLayoutInfo.containsKey(vh));
        assertTrue(mAnimator.postLayoutInfo.containsKey(vh));
        assertEquals(FLAG_CHANGED, log.preInfo.changeFlags);
        assertEquals(0, log.postInfo.changeFlags);
        //others should not receive anything
        for (int i = 0; i < mTestAdapter.getItemCount(); i++) {
            if (i == 2) {
                continue;
            }
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testUpdateWithDuplicateViewHolder() throws Throwable {
        setupBasic(10);
        final RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);
        mAnimator.canReUseCallback = new CanReUseCallback() {
            @Override
            public boolean canReUse(RecyclerView.ViewHolder viewHolder) {
                assertSame(viewHolder, vh);
                return false;
            }
        };
        mLayoutManager.expectLayouts(2);
        mTestAdapter.changeAndNotify(2, 1);
        mLayoutManager.waitForLayout(2);
        final RecyclerView.ViewHolder newVh = mRecyclerView.findViewHolderForAdapterPosition(2);
        assertNotSame(vh, newVh);
        assertEquals(1, mAnimator.animateChangeList.size());
        AnimateChange log = mAnimator.animateChangeList.get(0);
        assertSame(vh, log.viewHolder);
        assertSame(newVh, log.newHolder);
        assertNull(vh.itemView.getParent());
        assertTrue(mAnimator.preLayoutInfo.containsKey(vh));
        assertFalse(mAnimator.postLayoutInfo.containsKey(vh));
        assertTrue(mAnimator.postLayoutInfo.containsKey(newVh));
        assertEquals(FLAG_CHANGED, log.preInfo.changeFlags);
        assertEquals(0, log.postInfo.changeFlags);
        //others should not receive anything
        for (int i = 0; i < mTestAdapter.getItemCount(); i++) {
            if (i == 2) {
                continue;
            }
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testUpdateWithOneDuplicateAndOneInPlace() throws Throwable {
        setupBasic(10);
        final RecyclerView.ViewHolder replaced = mRecyclerView.findViewHolderForAdapterPosition(2);
        final RecyclerView.ViewHolder reused = mRecyclerView.findViewHolderForAdapterPosition(3);
        mAnimator.canReUseCallback = new CanReUseCallback() {
            @Override
            public boolean canReUse(RecyclerView.ViewHolder viewHolder) {
                if (viewHolder == replaced) {
                    return false;
                } else if (viewHolder == reused) {
                    return true;
                }
                fail("unpexpected view");
                return false;
            }
        };
        mLayoutManager.expectLayouts(2);
        mTestAdapter.changeAndNotify(2, 2);
        mLayoutManager.waitForLayout(2);
        final RecyclerView.ViewHolder newVh = mRecyclerView.findViewHolderForAdapterPosition(2);

        assertNotSame(replaced, newVh);
        assertSame(reused, mRecyclerView.findViewHolderForAdapterPosition(3));

        assertEquals(2, mAnimator.animateChangeList.size());
        AnimateChange logReplaced = null, logReused = null;
        for (AnimateChange change : mAnimator.animateChangeList) {
            if (change.newHolder == change.viewHolder) {
                logReused = change;
            } else {
                logReplaced = change;
            }
        }
        assertNotNull(logReplaced);
        assertNotNull(logReused);
        assertSame(replaced, logReplaced.viewHolder);
        assertSame(newVh, logReplaced.newHolder);
        assertSame(reused, logReused.viewHolder);
        assertSame(reused, logReused.newHolder);

        assertTrue(mAnimator.preLayoutInfo.containsKey(replaced));
        assertTrue(mAnimator.preLayoutInfo.containsKey(reused));

        assertTrue(mAnimator.postLayoutInfo.containsKey(newVh));
        assertTrue(mAnimator.postLayoutInfo.containsKey(reused));
        assertFalse(mAnimator.postLayoutInfo.containsKey(replaced));

        assertEquals(FLAG_CHANGED, logReplaced.preInfo.changeFlags);
        assertEquals(FLAG_CHANGED, logReused.preInfo.changeFlags);

        assertEquals(0, logReplaced.postInfo.changeFlags);
        assertEquals(0, logReused.postInfo.changeFlags);
        //others should not receive anything
        for (int i = 0; i < mTestAdapter.getItemCount(); i++) {
            if (i == 2 || i == 3) {
                continue;
            }
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testChangeToDisappear() throws Throwable {
        setupBasic(10);
        RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(9);
        mLayoutManager.mOnLayoutCallbacks.mLayoutItemCount = 9;
        mLayoutManager.expectLayouts(2);
        mTestAdapter.changeAndNotify(9, 1);
        mLayoutManager.waitForLayout(2);
        assertEquals(1, mAnimator.animateDisappearanceList.size());
        AnimateDisappearance log = mAnimator.animateDisappearanceList.get(0);
        assertSame(vh, log.viewHolder);
        assertFalse(mAnimator.postLayoutInfo.containsKey(vh));
        assertEquals(FLAG_CHANGED, log.preInfo.changeFlags);
        assertEquals(0, mAnimator.animateChangeList.size());
        assertEquals(0, mAnimator.animateAppearanceList.size());
        assertEquals(9, mAnimator.animatePersistenceList.size());
        checkForMainThreadException();
    }

    public void testUpdatePayload() throws Throwable {
        setupBasic(10);
        RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);
        mLayoutManager.expectLayouts(2);
        Object payload = new Object();
        mTestAdapter.changeAndNotifyWithPayload(2, 1, payload);
        mLayoutManager.waitForLayout(2);
        assertEquals(1, mAnimator.animateChangeList.size());
        AnimateChange log = mAnimator.animateChangeList.get(0);
        assertSame(vh, log.viewHolder);
        assertSame(vh, log.newHolder);
        assertTrue(mAnimator.preLayoutInfo.containsKey(vh));
        assertTrue(mAnimator.postLayoutInfo.containsKey(vh));
        assertEquals(FLAG_CHANGED, log.preInfo.changeFlags);
        assertEquals(0, log.postInfo.changeFlags);
        assertNotNull(log.preInfo.payloads);
        assertTrue(log.preInfo.payloads.contains(payload));
        //others should not receive anything
        for (int i = 0; i < mTestAdapter.getItemCount(); i++) {
            if (i == 2) {
                continue;
            }
            RecyclerView.ViewHolder other = mRecyclerView.findViewHolderForAdapterPosition(i);
            assertEquals(0, mAnimator.preLayoutInfo.get(other).changeFlags);
        }
        checkForMainThreadException();
    }

    public void testNotifyDataSetChanged() throws Throwable {
        TestAdapter adapter = new TestAdapter(10);
        adapter.setHasStableIds(true);
        setupBasic(10, 0, 10, adapter);
        mLayoutManager.expectLayouts(1);
        mTestAdapter.dispatchDataSetChanged();
        mLayoutManager.waitForLayout(2);
        assertEquals(10, mAnimator.animateChangeList.size());
        for (AnimateChange change : mAnimator.animateChangeList) {
            assertNotNull(change.preInfo);
            assertNotNull(change.postInfo);
            assertSame(change.preInfo.viewHolder, change.postInfo.viewHolder);
        }
        assertEquals(0, mAnimator.animatePersistenceList.size());
        assertEquals(0, mAnimator.animateAppearanceList.size());
        assertEquals(0, mAnimator.animateDisappearanceList.size());
    }

    public void testNotifyDataSetChangedWithoutStableIds() throws Throwable {
        TestAdapter adapter = new TestAdapter(10);
        adapter.setHasStableIds(false);
        setupBasic(10, 0, 10, adapter);
        mLayoutManager.expectLayouts(1);
        mTestAdapter.dispatchDataSetChanged();
        mLayoutManager.waitForLayout(2);
        assertEquals(0, mAnimator.animateChangeList.size());
        assertEquals(0, mAnimator.animatePersistenceList.size());
        assertEquals(0, mAnimator.animateAppearanceList.size());
        assertEquals(0, mAnimator.animateDisappearanceList.size());
    }

    public void testNotifyDataSetChangedWithAppearing() throws Throwable {
        notifyDataSetChangedWithAppearing(false);
    }

    public void testNotifyDataSetChangedWithAppearingNotifyBoth() throws Throwable {
        notifyDataSetChangedWithAppearing(true);
    }

    public void notifyDataSetChangedWithAppearing(final boolean notifyBoth) throws Throwable {
        final TestAdapter adapter = new TestAdapter(10);
        adapter.setHasStableIds(true);
        setupBasic(10, 0, 10, adapter);
        mLayoutManager.expectLayouts(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (notifyBoth) {
                        adapter.addAndNotify(2, 2);
                    } else {
                        adapter.mItems.add(2, new Item(2, "custom 1"));
                        adapter.mItems.add(3, new Item(3, "custom 2"));
                    }

                    adapter.notifyDataSetChanged();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });
        mLayoutManager.waitForLayout(2);
        assertEquals(10, mAnimator.animateChangeList.size());
        assertEquals(0, mAnimator.animatePersistenceList.size());
        assertEquals(2, mAnimator.animateAppearanceList.size());
        assertEquals(0, mAnimator.animateDisappearanceList.size());
    }

    public void testNotifyDataSetChangedWithDispappearing() throws Throwable {
        notifyDataSetChangedWithDispappearing(false);
    }

    public void testNotifyDataSetChangedWithDispappearingNotifyBoth() throws Throwable {
        notifyDataSetChangedWithDispappearing(true);
    }

    public void notifyDataSetChangedWithDispappearing(final boolean notifyBoth) throws Throwable {
        final TestAdapter adapter = new TestAdapter(10);
        adapter.setHasStableIds(true);
        setupBasic(10, 0, 10, adapter);
        mLayoutManager.expectLayouts(1);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (notifyBoth) {
                        adapter.deleteAndNotify(2, 2);
                    } else {
                        adapter.mItems.remove(2);
                        adapter.mItems.remove(2);
                    }
                    adapter.notifyDataSetChanged();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });
        mLayoutManager.waitForLayout(2);
        assertEquals(8, mAnimator.animateChangeList.size());
        assertEquals(0, mAnimator.animatePersistenceList.size());
        assertEquals(0, mAnimator.animateAppearanceList.size());
        assertEquals(2, mAnimator.animateDisappearanceList.size());
    }

    public void testNotifyUpdateWithChangedAdapterType() throws Throwable {
        final AtomicInteger itemType = new AtomicInteger(1);
        final TestAdapter adapter = new TestAdapter(10) {
            @Override
            public int getItemViewType(int position) {
                return position == 2 ? itemType.get() : 20;
            }
        };
        adapter.setHasStableIds(true);
        setupBasic(10, 0, 10, adapter);
        final RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(2);

        mAnimator.canReUseCallback = new CanReUseCallback() {
            @Override
            public boolean canReUse(RecyclerView.ViewHolder viewHolder) {
                return viewHolder != vh;
            }
        };

        mLayoutManager.expectLayouts(1);
        itemType.set(3);
        adapter.dispatchDataSetChanged();
        mLayoutManager.waitForLayout(2);
        final RecyclerView.ViewHolder newVh = mRecyclerView.findViewHolderForAdapterPosition(2);
        // TODO we should be able to map old type to the new one but doing that change has some
        // recycling side effects.
        assertEquals(9, mAnimator.animateChangeList.size());
        assertEquals(0, mAnimator.animatePersistenceList.size());
        assertEquals(1, mAnimator.animateAppearanceList.size());
        assertEquals(0, mAnimator.animateDisappearanceList.size());
        assertNotSame(vh, newVh);
        for (AnimateChange change : mAnimator.animateChangeList) {
            if (change.viewHolder == vh) {
                assertSame(change.newHolder, newVh);
                assertSame(change.viewHolder, vh);
            } else {
                assertSame(change.newHolder, change.viewHolder);
            }
        }
    }

    LoggingV2Animator mAnimator = new LoggingV2Animator();

    class LoggingV2Animator extends RecyclerView.ItemAnimator {

        CanReUseCallback canReUseCallback = new CanReUseCallback() {
            @Override
            public boolean canReUse(RecyclerView.ViewHolder viewHolder) {
                return true;
            }
        };
        Map<RecyclerView.ViewHolder, LoggingInfo> preLayoutInfo = new HashMap<>();
        Map<RecyclerView.ViewHolder, LoggingInfo> postLayoutInfo = new HashMap<>();

        List<AnimateAppearance> animateAppearanceList = new ArrayList<>();
        List<AnimateDisappearance> animateDisappearanceList = new ArrayList<>();
        List<AnimatePersistence> animatePersistenceList = new ArrayList<>();
        List<AnimateChange> animateChangeList = new ArrayList<>();

        @Override
        public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder) {
            return canReUseCallback.canReUse(viewHolder);
        }

        @Override
        public ItemHolderInfo recordPreLayoutInformation(RecyclerView.State state,
                RecyclerView.ViewHolder viewHolder,
                @AdapterChanges int changeFlags, List<Object> payloads) {
            LoggingInfo loggingInfo = new LoggingInfo(viewHolder, changeFlags, payloads);
            preLayoutInfo.put(viewHolder, loggingInfo);
            return loggingInfo;
        }

        @Override
        public ItemHolderInfo recordPostLayoutInformation(RecyclerView.State state,
                RecyclerView.ViewHolder viewHolder) {
            LoggingInfo loggingInfo = new LoggingInfo(viewHolder, 0, null);
            postLayoutInfo.put(viewHolder, loggingInfo);
            return loggingInfo;
        }

        @Override
        public boolean animateDisappearance(RecyclerView.ViewHolder viewHolder,
                ItemHolderInfo preInfo) {
            animateDisappearanceList.add(new AnimateDisappearance(viewHolder, preInfo));
            assertSame(preLayoutInfo.get(viewHolder), preInfo);
            dispatchAnimationFinished(viewHolder);

            return false;
        }

        @Override
        public boolean animateAppearance(RecyclerView.ViewHolder viewHolder, ItemHolderInfo preInfo,
                ItemHolderInfo postInfo) {
            animateAppearanceList.add(
                    new AnimateAppearance(viewHolder, preInfo, postInfo));
            assertSame(preLayoutInfo.get(viewHolder), preInfo);
            assertSame(postLayoutInfo.get(viewHolder), postInfo);
            dispatchAnimationFinished(viewHolder);
            return false;
        }

        @Override
        public boolean animatePersistence(RecyclerView.ViewHolder viewHolder,
                ItemHolderInfo preInfo,
                ItemHolderInfo postInfo) {
            animatePersistenceList.add(new AnimatePersistence(viewHolder, preInfo, postInfo));
            dispatchAnimationFinished(viewHolder);
            assertSame(preLayoutInfo.get(viewHolder), preInfo);
            assertSame(postLayoutInfo.get(viewHolder), postInfo);
            return false;
        }

        @Override
        public boolean animateChange(RecyclerView.ViewHolder oldHolder,
                RecyclerView.ViewHolder newHolder, ItemHolderInfo preInfo,
                ItemHolderInfo postInfo) {
            animateChangeList.add(new AnimateChange(oldHolder, newHolder, preInfo, postInfo));
            if (oldHolder != null) {
                dispatchAnimationFinished(oldHolder);
                assertSame(preLayoutInfo.get(oldHolder), preInfo);
            }
            if (newHolder != null) {
                dispatchAnimationFinished(newHolder);
                assertSame(postLayoutInfo.get(newHolder), postInfo);
            }

            return false;
        }

        @Override
        public void runPendingAnimations() {

        }

        @Override
        public void endAnimation(RecyclerView.ViewHolder item) {
        }

        @Override
        public void endAnimations() {

        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }

    static class LoggingInfo extends RecyclerView.ItemAnimator.ItemHolderInfo {

        final RecyclerView.ViewHolder viewHolder;
        @RecyclerView.ItemAnimator.AdapterChanges
        final int changeFlags;
        final List<Object> payloads;

        LoggingInfo(RecyclerView.ViewHolder viewHolder, int changeFlags, List<Object> payloads) {
            this.viewHolder = viewHolder;
            this.changeFlags = changeFlags;
            if (payloads != null) {
                this.payloads = new ArrayList<>();
                this.payloads.addAll(payloads);
            } else {
                this.payloads = null;
            }
            setFrom(viewHolder);
        }
    }

    static class AnimateChange extends AnimatePersistence {

        final RecyclerView.ViewHolder newHolder;

        public AnimateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                Object pre, Object post) {
            super(oldHolder, pre, post);
            this.newHolder = newHolder;
        }
    }

    static class AnimatePersistence extends AnimateAppearance {

        public AnimatePersistence(RecyclerView.ViewHolder viewHolder, Object pre, Object post) {
            super(viewHolder, pre, post);
        }
    }

    static class AnimateAppearance extends AnimateDisappearance {

        final LoggingInfo postInfo;

        public AnimateAppearance(RecyclerView.ViewHolder viewHolder, Object pre, Object post) {
            super(viewHolder, pre);
            this.postInfo = (LoggingInfo) post;
        }
    }

    static class AnimateDisappearance {

        final RecyclerView.ViewHolder viewHolder;
        final LoggingInfo preInfo;

        public AnimateDisappearance(RecyclerView.ViewHolder viewHolder, Object pre) {
            this.viewHolder = viewHolder;
            this.preInfo = (LoggingInfo) pre;
        }
    }

    interface CanReUseCallback {

        boolean canReUse(RecyclerView.ViewHolder viewHolder);
    }
}
