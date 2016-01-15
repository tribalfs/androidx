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
package android.support.percent;

import android.os.Build;
import android.support.percent.test.R;
import android.support.v4.view.ViewCompat;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.view.View;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

@SmallTest
public class PercentFrameTest extends BaseInstrumentationTestCase<TestFrameActivity> {
    private PercentFrameLayout mPercentFrameLayout;
    private int mContainerWidth;
    private int mContainerHeight;

    public PercentFrameTest() {
        super(TestFrameActivity.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        final TestFrameActivity activity = getActivity();
        mPercentFrameLayout = (PercentFrameLayout) activity.findViewById(R.id.container);
        mContainerWidth = mPercentFrameLayout.getWidth();
        mContainerHeight = mPercentFrameLayout.getHeight();
    }

    private void assertFuzzyEquals(String description, float expected, float actual) {
        float difference = actual - expected;
        // On devices with certain screen densities we may run into situations where multiplying
        // container width / height by a certain fraction ends up in a number that is almost but
        // not exactly a round float number. For example, we can do float math to compute 15%
        // of 1440 pixels and get 216.00002 due to inexactness of float math. This is why our
        // tolerance is slightly bigger than 1 pixel in the comparison below.
        if (Math.abs(difference) > 1.1) {
            Assert.fail(description + ": the difference between expected [" + expected +
                    "] and actual [" + actual + "] is not within the tolerance bound");
        }
    }

    @Test
    @UiThreadTest
    public void testWidthHeight() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_width_height);

        int childWidth = childToTest.getWidth();
        int childHeight = childToTest.getHeight();

        assertFuzzyEquals("Child width as 50% of the container",
                0.5f * mContainerWidth, childWidth);
        assertFuzzyEquals("Child height as 50% of the container",
                0.5f * mContainerHeight, childHeight);
    }

    @Test
    @UiThreadTest
    public void testWidthAspectRatio() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_width_ratio);

        int childWidth = childToTest.getWidth();
        int childHeight = childToTest.getHeight();

        assertFuzzyEquals("Child width as 60% of the container",
                0.6f * mContainerWidth, childWidth);
        assertFuzzyEquals("Child aspect ratio of 120%",
                childWidth / 1.2f, childHeight);
    }

    @Test
    @UiThreadTest
    public void testHeightAspectRatio() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_height_ratio);

        int childWidth = childToTest.getWidth();
        int childHeight = childToTest.getHeight();

        assertFuzzyEquals("Child height as 50% of the container",
                0.5f * mContainerHeight, childHeight);
        assertFuzzyEquals("Child aspect ratio of 150%",
                1.5f * childHeight, childWidth);
    }

    @Test
    @UiThreadTest
    public void testMarginsSingle() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margins_single);

        int childLeft = childToTest.getLeft();
        int childTop = childToTest.getTop();
        int childRight = childToTest.getRight();
        int childBottom = childToTest.getBottom();

        assertFuzzyEquals("Child left margin as 30% of the container",
                0.3f * mContainerWidth, childLeft);
        assertFuzzyEquals("Child top margin as 30% of the container",
                0.3f * mContainerHeight, childTop);
        assertFuzzyEquals("Child right margin as 30% of the container",
                0.3f * mContainerWidth, mContainerWidth - childRight);
        assertFuzzyEquals("Child bottom margin as 30% of the container",
                0.3f * mContainerHeight, mContainerHeight - childBottom);
    }

    @Test
    @UiThreadTest
    public void testMarginsMultiple() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margins_multiple);

        int childLeft = childToTest.getLeft();
        int childTop = childToTest.getTop();
        int childRight = childToTest.getRight();
        int childBottom = childToTest.getBottom();

        assertFuzzyEquals("Child top margin as 10% of the container",
                0.1f * mContainerHeight, childTop);
        assertFuzzyEquals("Child left margin as 15% of the container",
                0.15f * mContainerWidth, childLeft);
        assertFuzzyEquals("Child bottom margin as 20% of the container",
                0.2f * mContainerHeight, mContainerHeight - childBottom);
        assertFuzzyEquals("Child right margin as 25% of the container",
                0.25f * mContainerWidth, mContainerWidth - childRight);
    }

    @Test
    @UiThreadTest
    public void testMarginsTopLeft() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margins_top_left);

        int childWidth = childToTest.getWidth();
        int childHeight = childToTest.getHeight();
        int childLeft = childToTest.getLeft();
        int childTop = childToTest.getTop();

        assertFuzzyEquals("Child width as 50% of the container",
                0.5f * mContainerWidth, childWidth);
        assertFuzzyEquals("Child height as 50% of the container",
                0.5f * mContainerHeight, childHeight);
        assertFuzzyEquals("Child left margin as 20% of the container",
                0.2f * mContainerWidth, childLeft);
        assertFuzzyEquals("Child top margin as 20% of the container",
                0.2f * mContainerHeight, childTop);
    }

    @Test
    @UiThreadTest
    public void testMarginsBottomRight() {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margins_bottom_right);

        int childWidth = childToTest.getWidth();
        int childHeight = childToTest.getHeight();
        int childRight = childToTest.getRight();
        int childBottom = childToTest.getBottom();

        //Debug.waitForDebugger();
        assertFuzzyEquals("Child width as 60% of the container",
                0.6f * mContainerWidth, childWidth);
        assertFuzzyEquals("Child height as 60% of the container",
                0.6f * mContainerHeight, childHeight);
        assertFuzzyEquals("Child right margin as 10% of the container",
                0.1f * mContainerWidth, mContainerWidth - childRight);
        assertFuzzyEquals("Child bottom margin as 10% of the container",
                0.1f * mContainerHeight, mContainerHeight - childBottom);
    }

    @Test
    @UiThreadTest
    public void testMarginStart() throws Throwable {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margin_start);

        // Under LTR test that start is treated as left
        int childLeft = childToTest.getLeft();
        assertFuzzyEquals("Child start margin as 20% of the container",
                0.2f * mContainerWidth, childLeft);
    }

    @Test
    @UiThreadTest
    public void testMarginStartRtl() throws Throwable {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margin_start);

        if (Build.VERSION.SDK_INT >= 17) {
            // Force our child to inherit parent's layout direction
            onView(withId(R.id.child_margin_start)).perform(
                    LayoutDirectionActions.setLayoutDirection(ViewCompat.LAYOUT_DIRECTION_INHERIT));
            // And force the container to RTL mode
            onView(withId(R.id.container)).perform(
                    LayoutDirectionActions.setLayoutDirection(ViewCompat.LAYOUT_DIRECTION_RTL));

            // Force a full measure + layout pass on the container
            mPercentFrameLayout.measure(
                    View.MeasureSpec.makeMeasureSpec(mContainerWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(mContainerHeight, View.MeasureSpec.EXACTLY));
            mPercentFrameLayout.layout(mPercentFrameLayout.getLeft(),
                    mPercentFrameLayout.getTop(), mPercentFrameLayout.getRight(),
                    mPercentFrameLayout.getBottom());

            // Start under RTL should be treated as right
            int childRight = childToTest.getRight();
            assertFuzzyEquals("Child start margin as 20% of the container",
                    0.2f * mContainerWidth, mContainerWidth - childRight);
        } else {
            // On pre-v17 devices test that start is treated as left
            int childLeft = childToTest.getLeft();
            assertFuzzyEquals("Child start margin as 20% of the container",
                    0.2f * mContainerWidth, childLeft);
        }
    }

    @Test
    @UiThreadTest
    public void testMarginEnd() throws Throwable {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margin_end);

        // Under LTR test that end is treated as right
        int childRight = childToTest.getRight();
        assertFuzzyEquals("Child end margin as 30% of the container",
                0.3f * mContainerWidth, mContainerWidth - childRight);
    }

    @Test
    @UiThreadTest
    public void testMarginEndRtl() throws Throwable {
        View childToTest = mPercentFrameLayout.findViewById(R.id.child_margin_end);

        if (Build.VERSION.SDK_INT >= 17) {
            // Force our child to inherit parent's layout direction
            onView(withId(R.id.child_margin_end)).perform(
                    LayoutDirectionActions.setLayoutDirection(ViewCompat.LAYOUT_DIRECTION_INHERIT));
            // And force the container to RTL mode
            onView(withId(R.id.container)).perform(
                    LayoutDirectionActions.setLayoutDirection(ViewCompat.LAYOUT_DIRECTION_RTL));

            // Force a full measure + layout pass on the container
            mPercentFrameLayout.measure(
                    View.MeasureSpec.makeMeasureSpec(mContainerWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(mContainerHeight, View.MeasureSpec.EXACTLY));
            mPercentFrameLayout.layout(mPercentFrameLayout.getLeft(),
                    mPercentFrameLayout.getTop(), mPercentFrameLayout.getRight(),
                    mPercentFrameLayout.getBottom());

            // End under RTL should be treated as left
            int childLeft = childToTest.getLeft();
            assertFuzzyEquals("Child end margin as 30% of the container",
                    0.3f * mContainerWidth, childLeft);
        } else {
            // On pre-v17 devices test that end is treated as right
            int childRight = childToTest.getRight();
            assertFuzzyEquals("Child end margin as 30% of the container",
                    0.3f * mContainerWidth, mContainerWidth - childRight);
        }
    }
}
