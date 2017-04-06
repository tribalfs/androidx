/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.wearable.view;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.wearable.view.util.AsyncViewActions.waitForMatchingView;
import static android.support.wearable.view.util.MoreViewAssertions.withNoVerticalScrollOffset;
import static android.support.wearable.view.util.MoreViewAssertions.withPositiveVerticalScrollOffset;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.Matchers.allOf;

import android.app.Activity;
import android.support.annotation.IdRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.GeneralLocation;
import android.support.test.espresso.action.GeneralSwipeAction;
import android.support.test.espresso.action.Press;
import android.support.test.espresso.action.Swipe;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.test.R;
import android.support.wearable.view.util.WakeLockRule;
import android.view.View;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WearableRecyclerViewTest {

    private static final long MAX_WAIT_TIME = 10000;
    @Mock
    WearableRecyclerView.LayoutManager mMockChildLayoutManager;

    @Rule
    public final WakeLockRule wakeLock = new WakeLockRule();

    @Rule
    public final ActivityTestRule<WearableRecyclerViewTestActivity> mActivityRule =
            new ActivityTestRule<>(WearableRecyclerViewTestActivity.class, true, true);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCaseInitState() {
        WearableRecyclerView wrv = new WearableRecyclerView(mActivityRule.getActivity());
        wrv.setLayoutManager(new WearableLinearLayoutManager(wrv.getContext()));

        assertFalse(wrv.getEdgeItemsCenteringEnabled());
        assertFalse(wrv.isCircularScrollingGestureEnabled());
        assertEquals(1.0f, wrv.getBezelWidthFraction());
        assertEquals(180.0f, wrv.getScrollDegreesPerScreen());
    }

    @Test
    public void testEdgeItemsCenteringOnAndOff() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                wrv.setEdgeItemsCenteringEnabled(true);
            }
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                View child = wrv.getChildAt(0);
                assertNotNull("child", child);
                assertEquals((wrv.getHeight() - child.getHeight()) / 2, child.getTop());
            }
        });

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                wrv.setEdgeItemsCenteringEnabled(false);
            }
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                View child = wrv.getChildAt(0);
                assertNotNull("child", child);
                assertEquals(0, child.getTop());

            }
        });
    }

    @Test
    public void testEdgeItemsCenteringBeforeChildrenDrawn() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Activity activity = mActivityRule.getActivity();
                WearableRecyclerView wrv = (WearableRecyclerView) activity.findViewById(R.id.wrv);
                RecyclerView.Adapter<WearableRecyclerView.ViewHolder> adapter = wrv.getAdapter();
                wrv.setAdapter(null);
                wrv.setEdgeItemsCenteringEnabled(true);
                wrv.setAdapter(adapter);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                // Verify the first child
                View child = wrv.getChildAt(0);
                assertNotNull("child", child);
                assertEquals((wrv.getHeight() - child.getHeight()) / 2, child.getTop());
            }
        });
    }

    @Test
    public void testCircularScrollingGesture() throws Throwable {
        onView(withId(R.id.wrv)).perform(swipeDownFromTopRight());
        assertNotScrolledY(R.id.wrv);

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                wrv.setCircularScrollingGestureEnabled(true);
            }
        });

        onView(withId(R.id.wrv)).perform(swipeDownFromTopRight());
        assertScrolledY(R.id.wrv);
    }

    @Test
    public void testCurvedOffsettingHelper() throws Throwable {
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WearableRecyclerView wrv =
                        (WearableRecyclerView) mActivityRule.getActivity().findViewById(R.id.wrv);
                wrv.setLayoutManager(new WearableLinearLayoutManager(wrv.getContext()));
            }
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        onView(withId(R.id.wrv)).perform(swipeDownFromTopRight());

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Activity activity = mActivityRule.getActivity();
                WearableRecyclerView wrv = (WearableRecyclerView) activity.findViewById(R.id.wrv);
                if (activity.getResources().getConfiguration().isScreenRound()) {
                    View child = wrv.getChildAt(0);
                    assertTrue(child.getLeft() > 0);
                } else {
                    for (int i = 0; i < wrv.getChildCount(); i++) {
                        assertEquals(0, wrv.getChildAt(i).getLeft());
                    }
                }
            }
        });
    }

    private static ViewAction swipeDownFromTopRight() {
        return new GeneralSwipeAction(
                Swipe.FAST, GeneralLocation.TOP_RIGHT, GeneralLocation.BOTTOM_RIGHT,
                Press.FINGER);
    }

    private void assertScrolledY(@IdRes int layoutId) {
        onView(withId(layoutId)).perform(waitForMatchingView(
                allOf(withId(layoutId), withPositiveVerticalScrollOffset()), MAX_WAIT_TIME));
    }

    private void assertNotScrolledY(@IdRes int layoutId) {
        onView(withId(layoutId)).perform(waitForMatchingView(
                allOf(withId(layoutId), withNoVerticalScrollOffset()), MAX_WAIT_TIME));
    }
}
