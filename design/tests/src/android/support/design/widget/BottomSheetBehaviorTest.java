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

package android.support.design.widget;

import org.hamcrest.Matcher;
import org.junit.Test;

import android.support.design.test.R;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.action.GeneralLocation;
import android.support.test.espresso.action.GeneralSwipeAction;
import android.support.test.espresso.action.Press;
import android.support.test.espresso.action.Swipe;
import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.assertion.ViewAssertions;
import android.support.test.espresso.matcher.ViewMatchers;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.view.ViewGroup;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class BottomSheetBehaviorTest extends
        BaseInstrumentationTestCase<BottomSheetBehaviorActivity> {

    public static class Callback extends BottomSheetBehavior.BottomSheetCallback
            implements IdlingResource {

        private boolean mIsIdle;

        private IdlingResource.ResourceCallback mResourceCallback;

        public Callback(BottomSheetBehavior behavior) {
            behavior.setBottomSheetCallback(this);
            int state = behavior.getState();
            mIsIdle = isIdleState(state);
        }

        @Override
        public void onStateChanged(@BottomSheetBehavior.State int newState) {
            boolean wasIdle = mIsIdle;
            mIsIdle = isIdleState(newState);
            if (!wasIdle && mIsIdle && mResourceCallback != null) {
                mResourceCallback.onTransitionToIdle();
            }
        }

        @Override
        public void onSlide(float slideOffset) {
        }

        @Override
        public String getName() {
            return Callback.class.getSimpleName();
        }

        @Override
        public boolean isIdleNow() {
            return mIsIdle;
        }

        @Override
        public void registerIdleTransitionCallback(IdlingResource.ResourceCallback callback) {
            mResourceCallback = callback;
        }

        private boolean isIdleState(int state) {
            return state != BottomSheetBehavior.STATE_DRAGGING &&
                    state != BottomSheetBehavior.STATE_SETTLING;
        }

    }

    private Callback mCallback;

    public BottomSheetBehaviorTest() {
        super(BottomSheetBehaviorActivity.class);
    }

    @Test
    @SmallTest
    public void testInitialSetup() {
        BottomSheetBehavior behavior = getBehavior();
        assertThat(behavior.getState(), is(BottomSheetBehavior.STATE_COLLAPSED));
        CoordinatorLayout coordinatorLayout = getCoordinatorLayout();
        ViewGroup bottomSheet = getBottomSheet();
        assertThat(bottomSheet.getTop(),
                is(coordinatorLayout.getHeight() - behavior.getPeekHeight()));
    }

    @Test
    @MediumTest
    public void testSetStateExpandedToCollapsed() {
        checkSetState(BottomSheetBehavior.STATE_EXPANDED, ViewMatchers.isDisplayed());
        checkSetState(BottomSheetBehavior.STATE_COLLAPSED, ViewMatchers.isDisplayed());
    }

    @Test
    @MediumTest
    public void testSetStateHiddenToCollapsed() {
        checkSetState(BottomSheetBehavior.STATE_HIDDEN, not(ViewMatchers.isDisplayed()));
        checkSetState(BottomSheetBehavior.STATE_COLLAPSED, ViewMatchers.isDisplayed());
    }

    @Test
    @MediumTest
    public void testSwipeDownToCollapse() {
        checkSetState(BottomSheetBehavior.STATE_EXPANDED, ViewMatchers.isDisplayed());
        Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                .perform(DesignViewActions.withCustomConstraints(ViewActions.swipeDown(),
                        ViewMatchers.isDisplayingAtLeast(5)));
        // Avoid a deadlock (b/26160710)
        registerIdlingResourceCallback();
        try {
            Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                    .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            assertThat(getBehavior().getState(), is(BottomSheetBehavior.STATE_COLLAPSED));
        } finally {
            unregisterIdlingResourceCallback();
        }
    }

    @Test
    @MediumTest
    public void testSwipeDownToHide() {
        Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                .perform(DesignViewActions.withCustomConstraints(ViewActions.swipeDown(),
                        ViewMatchers.isDisplayingAtLeast(5)));
        // Avoid a deadlock (b/26160710)
        try {
            Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                    .check(ViewAssertions.matches(not(ViewMatchers.isDisplayed())));
            assertThat(getBehavior().getState(), is(BottomSheetBehavior.STATE_HIDDEN));
        } finally {
            unregisterIdlingResourceCallback();
        }
    }

    @Test
    @MediumTest
    public void testSwipeUpToExpand() {
        Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                .perform(DesignViewActions.withCustomConstraints(
                        new GeneralSwipeAction(Swipe.FAST,
                                GeneralLocation.VISIBLE_CENTER, GeneralLocation.TOP_CENTER,
                                Press.FINGER),
                        ViewMatchers.isDisplayingAtLeast(5)));
        // Avoid a deadlock (b/26160710)
        registerIdlingResourceCallback();
        try {
            Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                    .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            assertThat(getBehavior().getState(), is(BottomSheetBehavior.STATE_EXPANDED));
        } finally {
            unregisterIdlingResourceCallback();
        }
    }

    private void checkSetState(int state, Matcher<View> matcher) {
        registerIdlingResourceCallback();
        try {
            getBehavior().setState(state);
            Espresso.onView(ViewMatchers.withId(R.id.bottom_sheet))
                    .check(ViewAssertions.matches(matcher));
            assertThat(getBehavior().getState(), is(state));
        } finally {
            unregisterIdlingResourceCallback();
        }
    }

    private void registerIdlingResourceCallback() {
        // TODO(yaraki): Move this to setUp() when b/26160710 is fixed
        mCallback = new Callback(getBehavior());
        Espresso.registerIdlingResources(mCallback);
    }

    private void unregisterIdlingResourceCallback() {
        if (mCallback != null) {
            Espresso.unregisterIdlingResources(mCallback);
            mCallback = null;
        }
    }

    private ViewGroup getBottomSheet() {
        return getActivity().mBottomSheet;
    }

    private BottomSheetBehavior getBehavior() {
        return getActivity().mBehavior;
    }

    private CoordinatorLayout getCoordinatorLayout() {
        return getActivity().mCoordinatorLayout;
    }

}
