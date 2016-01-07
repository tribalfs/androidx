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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@MediumTest
@RunWith(Parameterized.class)
public class RecyclerViewAccessibilityTest extends BaseRecyclerViewInstrumentationTest {

    final boolean verticalScrollBefore, horizontalScrollBefore, verticalScrollAfter,
            horizontalScrollAfter;

    public RecyclerViewAccessibilityTest(boolean verticalScrollBefore,
            boolean horizontalScrollBefore, boolean verticalScrollAfter,
            boolean horizontalScrollAfter) {
        this.verticalScrollBefore = verticalScrollBefore;
        this.horizontalScrollBefore = horizontalScrollBefore;
        this.verticalScrollAfter = verticalScrollAfter;
        this.horizontalScrollAfter = horizontalScrollAfter;
    }

    @Parameterized.Parameters(name = "vBefore={0} vAfter={1} hBefore={2} hAfter={3}")
    public static List<Object[]> getParams() {
        List<Object[]> params = new ArrayList<>();
        for (boolean vBefore : new boolean[]{true, false}) {
            for (boolean vAfter : new boolean[]{true, false}) {
                for (boolean hBefore : new boolean[]{true, false}) {
                    for (boolean hAfter : new boolean[]{true, false}) {
                        params.add(new Object[]{vBefore, hBefore, vAfter, hAfter});
                    }
                }
            }
        }
        return params;
    }

    @Test
    public void onInitializeAccessibilityNodeInfoTest() throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity()) {
            //@Override
            public boolean canScrollHorizontally(int direction) {
                return direction < 0 && horizontalScrollBefore ||
                        direction > 0 && horizontalScrollAfter;
            }

            //@Override
            public boolean canScrollVertically(int direction) {
                return direction < 0 && verticalScrollBefore ||
                        direction > 0 && verticalScrollAfter;
            }
        };
        final TestAdapter adapter = new TestAdapter(10);
        final AtomicBoolean hScrolledBack = new AtomicBoolean(false);
        final AtomicBoolean vScrolledBack = new AtomicBoolean(false);
        final AtomicBoolean hScrolledFwd = new AtomicBoolean(false);
        final AtomicBoolean vScrolledFwd = new AtomicBoolean(false);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new TestLayoutManager() {

            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                layoutRange(recycler, 0, 5);
            }

            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(-1, -1);
            }

            @Override
            public boolean canScrollVertically() {
                return verticalScrollAfter || verticalScrollBefore;
            }

            @Override
            public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,
                    RecyclerView.State state) {
                if (dx > 0) {
                    hScrolledFwd.set(true);
                } else if (dx < 0) {
                    hScrolledBack.set(true);
                }
                return 0;
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                    RecyclerView.State state) {
                if (dy > 0) {
                    vScrolledFwd.set(true);
                } else if (dy < 0) {
                    vScrolledBack.set(true);
                }
                return 0;
            }

            @Override
            public boolean canScrollHorizontally() {
                return horizontalScrollAfter || horizontalScrollBefore;
            }
        });
        setRecyclerView(recyclerView);
        final RecyclerViewAccessibilityDelegate delegateCompat = recyclerView
                .getCompatAccessibilityDelegate();
        final AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.onInitializeAccessibilityNodeInfo(recyclerView, info);
            }
        });
        assertEquals(horizontalScrollAfter || horizontalScrollBefore
                || verticalScrollAfter || verticalScrollBefore, info.isScrollable());
        assertEquals(horizontalScrollBefore || verticalScrollBefore,
                (info.getActions() & AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) != 0);
        assertEquals(horizontalScrollAfter || verticalScrollAfter,
                (info.getActions() & AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) != 0);
        final AccessibilityNodeInfoCompat.CollectionInfoCompat collectionInfo = info
                .getCollectionInfo();
        assertNotNull(collectionInfo);
        if (recyclerView.getLayoutManager().canScrollVertically()) {
            assertEquals(adapter.getItemCount(), collectionInfo.getRowCount());
        }
        if (recyclerView.getLayoutManager().canScrollHorizontally()) {
            assertEquals(adapter.getItemCount(), collectionInfo.getColumnCount());
        }

        final AccessibilityEvent event = AccessibilityEvent.obtain();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.onInitializeAccessibilityEvent(recyclerView, event);
            }
        });
        final AccessibilityRecordCompat record = AccessibilityEventCompat
                .asRecord(event);
        assertEquals(record.isScrollable(), verticalScrollAfter || horizontalScrollAfter ||
                verticalScrollBefore || horizontalScrollBefore);
        assertEquals(record.getItemCount(), adapter.getItemCount());

        getInstrumentation().waitForIdleSync();
        for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
            final View view = mRecyclerView.getChildAt(i);
            final AccessibilityNodeInfoCompat childInfo = AccessibilityNodeInfoCompat.obtain();
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    delegateCompat.getItemDelegate().
                            onInitializeAccessibilityNodeInfo(view, childInfo);
                }
            });
            final AccessibilityNodeInfoCompat.CollectionItemInfoCompat collectionItemInfo
                    = childInfo.getCollectionItemInfo();
            assertNotNull(collectionItemInfo);
            if (recyclerView.getLayoutManager().canScrollHorizontally()) {
                assertEquals(i, collectionItemInfo.getColumnIndex());
            } else {
                assertEquals(0, collectionItemInfo.getColumnIndex());
            }

            if (recyclerView.getLayoutManager().canScrollVertically()) {
                assertEquals(i, collectionItemInfo.getRowIndex());
            } else {
                assertEquals(0, collectionItemInfo.getRowIndex());
            }
        }

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
        hScrolledBack.set(false);
        vScrolledBack.set(false);
        hScrolledFwd.set(false);
        vScrolledBack.set(false);
        performAccessibilityAction(delegateCompat, recyclerView,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
        assertEquals(horizontalScrollBefore, hScrolledBack.get());
        assertEquals(verticalScrollBefore, vScrolledBack.get());
        assertEquals(false, hScrolledFwd.get());
        assertEquals(false, vScrolledFwd.get());

        hScrolledBack.set(false);
        vScrolledBack.set(false);
        hScrolledFwd.set(false);
        vScrolledBack.set(false);
        performAccessibilityAction(delegateCompat, recyclerView,
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        assertEquals(false, hScrolledBack.get());
        assertEquals(false, vScrolledBack.get());
        assertEquals(horizontalScrollAfter, hScrolledFwd.get());
        assertEquals(verticalScrollAfter, vScrolledFwd.get());
    }

    @Test
    public void ignoreAccessibilityIfAdapterHasChanged() throws Throwable {
        final RecyclerView recyclerView = new RecyclerView(getActivity()) {
            //@Override
            public boolean canScrollHorizontally(int direction) {
                return true;
            }

            //@Override
            public boolean canScrollVertically(int direction) {
                return true;
            }
        };
        final DumbLayoutManager layoutManager = new DumbLayoutManager();
        final TestAdapter adapter = new TestAdapter(10);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(layoutManager);
        layoutManager.expectLayouts(1);
        setRecyclerView(recyclerView);
        layoutManager.waitForLayout(1);

        final RecyclerViewAccessibilityDelegate delegateCompat = recyclerView
                .getCompatAccessibilityDelegate();
        final AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.onInitializeAccessibilityNodeInfo(recyclerView, info);
            }
        });
        assertTrue("test sanity", info.isScrollable());
        final AccessibilityNodeInfoCompat info2 = AccessibilityNodeInfoCompat.obtain();
        layoutManager.blockLayout();
        layoutManager.expectLayouts(1);
        adapter.deleteAndNotify(1, 1);
        // we can run this here since we blocked layout.
        delegateCompat.onInitializeAccessibilityNodeInfo(recyclerView, info2);
        layoutManager.unblockLayout();
        assertFalse("info should not be filled if data is out of date", info2.isScrollable());
        layoutManager.waitForLayout(1);
    }

    boolean performAccessibilityAction(final AccessibilityDelegateCompat delegate,
            final RecyclerView recyclerView, final int action) throws Throwable {
        final boolean[] result = new boolean[1];
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                result[0] = delegate.performAccessibilityAction(recyclerView, action, null);
            }
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(250);
        return result[0];
    }
}
