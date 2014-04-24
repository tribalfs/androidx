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

import android.test.AndroidTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class RecyclerViewTest extends AndroidTestCase {

    RecyclerView mRecyclerView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRecyclerView = new RecyclerView(mContext);
    }

    public void testMeasureWithoutLayoutManager() {
        Throwable measureThrowable = null;
        try {
            measure();
        } catch (Throwable throwable) {
            measureThrowable = throwable;
        }
        assertTrue("Calling measure without a layout manager should throw exception"
                , measureThrowable instanceof NullPointerException);
    }

    private void measure() {
        mRecyclerView.measure(View.MeasureSpec.AT_MOST | 320, View.MeasureSpec.AT_MOST | 240);
    }

    private void layout() {
        mRecyclerView.layout(0, 0, 320, 320);
    }

    private void safeLayout() {
        try {
            layout();
        } catch (Throwable t) {

        }
    }

    public void testLayoutWithoutLayoutManager() throws InterruptedException {
        MockLayoutManager layoutManager = new MockLayoutManager();
        mRecyclerView.setLayoutManager(layoutManager);
        safeLayout();
        assertEquals("layout manager should not be called if there is no adapter attached",
                0, layoutManager.mLayoutCount);
    }

    public void testLayout() throws InterruptedException {
        MockLayoutManager layoutManager = new MockLayoutManager();
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(new MockAdapter(3));
        layout();
        assertEquals("when both layout manager and activity is set, recycler view should call"
                + " layout manager's layout method", 1, layoutManager.mLayoutCount);
    }

    public void testObservingAdapters() {
        MockAdapter adapterOld = new MockAdapter(1);
        mRecyclerView.setAdapter(adapterOld);
        assertTrue("attached adapter should have observables", adapterOld.hasObservers());

        MockAdapter adapterNew = new MockAdapter(2);
        mRecyclerView.setAdapter(adapterNew);
        assertFalse("detached adapter should lose observable", adapterOld.hasObservers());
        assertTrue("new adapter should have observers", adapterNew.hasObservers());

        mRecyclerView.setAdapter(null);
        assertNull("adapter should be removed successfully", mRecyclerView.getAdapter());
        assertFalse("when adapter is removed, observables should be removed too",
                adapterNew.hasObservers());
    }

    public void testAdapterChangeCallbacks() {
        MockLayoutManager layoutManager = new MockLayoutManager();
        mRecyclerView.setLayoutManager(layoutManager);
        MockAdapter adapterOld = new MockAdapter(1);
        mRecyclerView.setAdapter(adapterOld);
        layoutManager.assertPrevNextAdapters(null, adapterOld);

        MockAdapter adapterNew = new MockAdapter(2);
        mRecyclerView.setAdapter(adapterNew);
        layoutManager.assertPrevNextAdapters("switching adapters should trigger correct callbacks"
                , adapterOld, adapterNew);

        mRecyclerView.setAdapter(null);
        layoutManager.assertPrevNextAdapters(
                "Setting adapter null should trigger correct callbacks",
                adapterNew, null);
    }

    private class MockLayoutManager extends RecyclerView.LayoutManager {

        int mLayoutCount = 0;

        int mAdapterChangedCount = 0;

        RecyclerView.Adapter mPrevAdapter;

        RecyclerView.Adapter mNextAdapter;

        @Override
        public void onAdapterChanged(RecyclerView.Adapter oldAdapter,
                RecyclerView.Adapter newAdapter) {
            super.onAdapterChanged(oldAdapter, newAdapter);
            mPrevAdapter = oldAdapter;
            mNextAdapter = newAdapter;
            mAdapterChangedCount++;
        }

        @Override
        public void layoutChildren(RecyclerView.Adapter adapter, RecyclerView.Recycler recycler,
                boolean structureChanged) {
            mLayoutCount += 1;
        }

        @Override
        public RecyclerView.LayoutParams generateDefaultLayoutParams() {
            return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        public void assertPrevNextAdapters(String message, RecyclerView.Adapter prevAdapter,
                RecyclerView.Adapter nextAdapter) {
            assertSame(message, prevAdapter, mPrevAdapter);
            assertSame(message, nextAdapter, mNextAdapter);
        }

        public void assertPrevNextAdapters(RecyclerView.Adapter prevAdapter,
                RecyclerView.Adapter nextAdapter) {
            assertPrevNextAdapters("Adapters from onAdapterChanged callback should match",
                    prevAdapter, nextAdapter);
        }
    }

    private class MockAdapter extends RecyclerView.Adapter {

        private int mCount = 0;

        private MockAdapter(int count) {
            this.mCount = count;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new MockViewHolder(new TextView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemCount() {
            return mCount;
        }
    }

    private class MockViewHolder extends RecyclerView.ViewHolder {

        public MockViewHolder(View itemView) {
            super(itemView);
        }
    }
}
