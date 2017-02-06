/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v17.leanback.testutils.PollingCheck;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.KeyEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.ref.WeakReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class RowsFragmentTest extends SingleFragmentTestBase {

    static final StringPresenter sCardPresenter = new StringPresenter();

    static void loadData(ArrayObjectAdapter adapter, int numRows, int repeatPerRow) {
        for (int i = 0; i < numRows; ++i) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(sCardPresenter);
            int index = 0;
            for (int j = 0; j < repeatPerRow; ++j) {
                listRowAdapter.add("Hello world-" + (index++));
                listRowAdapter.add("This is a test-" + (index++));
                listRowAdapter.add("Android TV-" + (index++));
                listRowAdapter.add("Leanback-" + (index++));
                listRowAdapter.add("Hello world-" + (index++));
                listRowAdapter.add("Android TV-" + (index++));
                listRowAdapter.add("Leanback-" + (index++));
                listRowAdapter.add("GuidedStepFragment-" + (index++));
            }
            HeaderItem header = new HeaderItem(i, "Row " + i);
            adapter.add(new ListRow(header, listRowAdapter));
        }
    }

    public static class F_defaultAlignment extends RowsFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ListRowPresenter lrp = new ListRowPresenter();
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(lrp);
            setAdapter(adapter);
            loadData(adapter, 10, 1);
        }
    }

    @Test
    public void defaultAlignment() throws InterruptedException {
        launchAndWaitActivity(1000);

        final Rect rect = new Rect();

        final VerticalGridView gridView = ((RowsFragment) mActivity.getTestFragment())
                .getVerticalGridView();
        View row0 = gridView.findViewHolderForAdapterPosition(0).itemView;
        rect.set(0, 0, row0.getWidth(), row0.getHeight());
        gridView.offsetDescendantRectToMyCoords(row0, rect);
        assertEquals("First row is initially aligned to top of screen", 0, rect.top);

        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        View row1 = gridView.findViewHolderForAdapterPosition(1).itemView;
        PollingCheck.waitFor(new PollingCheck.ViewStableOnScreen(row1));

        rect.set(0, 0, row1.getWidth(), row1.getHeight());
        gridView.offsetDescendantRectToMyCoords(row1, rect);
        assertTrue("Second row should not be aligned to top of screen", rect.top > 0);
    }

    public static class F_selectBeforeSetAdapter extends RowsFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setSelectedPosition(7, false);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getVerticalGridView().requestLayout();
                }
            }, 100);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    ListRowPresenter lrp = new ListRowPresenter();
                    ArrayObjectAdapter adapter = new ArrayObjectAdapter(lrp);
                    setAdapter(adapter);
                    loadData(adapter, 10, 1);
                }
            }, 1000);
        }
    }

    @Test
    public void selectBeforeSetAdapter() throws InterruptedException {
        launchAndWaitActivity(2000);

        final VerticalGridView gridView = ((RowsFragment) mActivity.getTestFragment())
                .getVerticalGridView();
        assertEquals(7, gridView.getSelectedPosition());
        assertNotNull(gridView.findViewHolderForAdapterPosition(7));
    }

    public static class F_selectBeforeAddData extends RowsFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ListRowPresenter lrp = new ListRowPresenter();
            final ArrayObjectAdapter adapter = new ArrayObjectAdapter(lrp);
            setAdapter(adapter);
            setSelectedPosition(7, false);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getVerticalGridView().requestLayout();
                }
            }, 100);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadData(adapter, 10, 1);
                }
            }, 1000);
        }
    }

    @Test
    public void selectBeforeAddData() throws InterruptedException {
        launchAndWaitActivity(2000);

        final VerticalGridView gridView = ((RowsFragment) mActivity.getTestFragment())
                .getVerticalGridView();
        assertEquals(7, gridView.getSelectedPosition());
        assertNotNull(gridView.findViewHolderForAdapterPosition(7));
    }

    public static class F_selectAfterAddData extends RowsFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ListRowPresenter lrp = new ListRowPresenter();
            final ArrayObjectAdapter adapter = new ArrayObjectAdapter(lrp);
            setAdapter(adapter);
            loadData(adapter, 10, 1);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setSelectedPosition(7, false);
                }
            }, 1000);
        }
    }

    @Test
    public void selectAfterAddData() throws InterruptedException {
        launchAndWaitActivity(2000);

        final VerticalGridView gridView = ((RowsFragment) mActivity.getTestFragment())
                .getVerticalGridView();
        assertEquals(7, gridView.getSelectedPosition());
        assertNotNull(gridView.findViewHolderForAdapterPosition(7));
    }

    static WeakReference<F_restoreSelection> sLastF_restoreSelection;

    public static class F_restoreSelection extends RowsFragment {
        public F_restoreSelection() {
            sLastF_restoreSelection = new WeakReference<F_restoreSelection>(this);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ListRowPresenter lrp = new ListRowPresenter();
            final ArrayObjectAdapter adapter = new ArrayObjectAdapter(lrp);
            setAdapter(adapter);
            loadData(adapter, 10, 1);
            if (savedInstanceState == null) {
                setSelectedPosition(7, false);
            }
        }
    }

    @Test
    public void restoreSelection() {
        launchAndWaitActivity(1000);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    public void run() {
                        mActivity.recreate();
                    }
                }
        );
        SystemClock.sleep(1000);

        // mActivity is invalid after recreate(), a new Activity instance is created
        // but we could get Fragment from static variable.
        RowsFragment fragment = sLastF_restoreSelection.get();
        final VerticalGridView gridView = fragment.getVerticalGridView();
        assertEquals(7, gridView.getSelectedPosition());
        assertNotNull(gridView.findViewHolderForAdapterPosition(7));

    }
}
