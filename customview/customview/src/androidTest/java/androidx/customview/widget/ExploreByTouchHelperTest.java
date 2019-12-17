/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.customview.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.test.R;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ExploreByTouchHelperTest {
    @Rule
    public final ActivityTestRule<ExploreByTouchHelperTestActivity> mActivityTestRule;

    private View mHost;

    public ExploreByTouchHelperTest() {
        mActivityTestRule = new ActivityTestRule<>(ExploreByTouchHelperTestActivity.class);
    }

    @Before
    public void setUp() {
        mHost = mActivityTestRule.getActivity().findViewById(R.id.host_view);
    }

    @Test
    @UiThreadTest
    public void testBoundsInScreen() {
        final ExploreByTouchHelper helper = new ParentBoundsHelper(mHost);
        ViewCompat.setAccessibilityDelegate(mHost, helper);

        final AccessibilityNodeInfoCompat node =
                helper.getAccessibilityNodeProvider(mHost).createAccessibilityNodeInfo(1);
        assertNotNull(node);

        final Rect hostBounds = new Rect();
        mHost.getLocalVisibleRect(hostBounds);
        assertFalse("Host has not been laid out", hostBounds.isEmpty());

        final Rect nodeBoundsInParent = new Rect();
        node.getBoundsInParent(nodeBoundsInParent);
        assertEquals("Wrong bounds in parent", hostBounds, nodeBoundsInParent);

        final Rect hostBoundsOnScreen = getBoundsOnScreen(mHost);
        final Rect nodeBoundsInScreen = new Rect();
        node.getBoundsInScreen(nodeBoundsInScreen);
        assertEquals("Wrong bounds in screen", hostBoundsOnScreen, nodeBoundsInScreen);

        final int scrollX = 100;
        final int scrollY = 50;
        mHost.scrollTo(scrollX, scrollY);

        // Generate a node for the new position.
        final AccessibilityNodeInfoCompat scrolledNode =
                helper.getAccessibilityNodeProvider(mHost).createAccessibilityNodeInfo(1);
        assertNotNull(scrolledNode);

        // Bounds in parent should not be affected by visibility.
        final Rect scrolledNodeBoundsInParent = new Rect();
        scrolledNode.getBoundsInParent(scrolledNodeBoundsInParent);
        assertEquals("Wrong bounds in parent after scrolling",
                hostBounds, scrolledNodeBoundsInParent);

        final Rect expectedBoundsInScreen = new Rect(hostBoundsOnScreen);
        expectedBoundsInScreen.offset(-scrollX, -scrollY);
        expectedBoundsInScreen.intersect(hostBoundsOnScreen);
        scrolledNode.getBoundsInScreen(nodeBoundsInScreen);
        assertEquals("Wrong bounds in screen after scrolling",
                expectedBoundsInScreen, nodeBoundsInScreen);

        ViewCompat.setAccessibilityDelegate(mHost, null);
    }
    @Test
    @UiThreadTest
    public void testMoveFocusToNextVirtualId() {
        final ExploreByTouchHelper helper = new FocusTouchHelper(mHost);

        ViewCompat.setAccessibilityDelegate(mHost, helper);

        boolean moveFocusToId1 = helper.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
        assertEquals(1, helper.getKeyboardFocusedVirtualViewId());
        assertEquals(true, moveFocusToId1);

        // moveFocus should move focus to the node with id 5
        boolean moveFocusToId5 = helper.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
        assertEquals(5, helper.getKeyboardFocusedVirtualViewId());
        assertEquals(true, moveFocusToId5);

        // moveFocus should not return true if the node has id INVALID_ID.
        boolean moveFocusToInvalidId = helper.dispatchKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
        assertEquals(ExploreByTouchHelper.INVALID_ID, helper.getKeyboardFocusedVirtualViewId());
        assertEquals(false, moveFocusToInvalidId);

        ViewCompat.setAccessibilityDelegate(mHost, null);
    }

    private static Rect getBoundsOnScreen(View v) {
        final int[] tempLocation = new int[2];
        final Rect hostBoundsOnScreen = new Rect(0, 0, v.getWidth(), v.getHeight());
        v.getLocationOnScreen(tempLocation);
        hostBoundsOnScreen.offset(tempLocation[0], tempLocation[1]);
        return hostBoundsOnScreen;
    }

    /**
     * An extension of ExploreByTouchHelper that contains a single virtual view
     * whose bounds match the host view.
     */
    private static class ParentBoundsHelper extends ExploreByTouchHelper {
        private final View mHost;

        ParentBoundsHelper(View host) {
            super(host);

            mHost = host;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            return 1;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            virtualViewIds.add(1);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId,
                @NonNull AccessibilityNodeInfoCompat node) {
            if (virtualViewId == 1) {
                node.setContentDescription("test");

                final Rect hostBounds = new Rect(0, 0, mHost.getWidth(), mHost.getHeight());
                node.setBoundsInParent(hostBounds);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action,
                Bundle arguments) {
            return false;
        }
    }

    /**
     * An extension of ExploreByTouchHelper that contains two virtual views to test moving focus.
     */
    private static class FocusTouchHelper extends ExploreByTouchHelper {
        private final View mHost;

        FocusTouchHelper(View host) {
            super(host);
            mHost = host;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            RectF topHalf = new RectF();
            topHalf.set(0, 0, mHost.getWidth(), mHost.getHeight() / 2);
            if (topHalf.contains(x, y)) {
                return 1;
            }
            return 5;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            virtualViewIds.add(1);
            virtualViewIds.add(5);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId,
                @NonNull AccessibilityNodeInfoCompat node) {
            if (virtualViewId == 1) {
                node.setContentDescription("test 1");
                final Rect hostBounds = new Rect(0, 0, mHost.getWidth(), mHost.getHeight() / 2);
                node.setBoundsInParent(hostBounds);
            }
            if (virtualViewId == 5) {
                node.setContentDescription("test 5");
                final Rect hostBounds =
                        new Rect(0, mHost.getHeight() / 2, mHost.getWidth(), mHost.getHeight());
                node.setBoundsInParent(hostBounds);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action,
                Bundle arguments) {
            return false;
        }

    }
}
