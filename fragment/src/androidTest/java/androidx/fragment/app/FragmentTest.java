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
package androidx.fragment.app;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.test.FragmentTestActivity;
import androidx.fragment.test.R;
import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Miscellaneous tests for fragments that aren't big enough to belong to their own classes.
 */
@RunWith(AndroidJUnit4.class)
public class FragmentTest {
    @Rule
    public ActivityTestRule<FragmentTestActivity> mActivityRule =
            new ActivityTestRule<FragmentTestActivity>(FragmentTestActivity.class);

    private FragmentTestActivity mActivity;
    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @SmallTest
    @UiThreadTest
    @Test
    public void testRequireView() {
        StrictViewFragment fragment1 = new StrictViewFragment();
        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.content, fragment1)
                .commitNow();
        assertThat(fragment1.requireView())
                .isNotNull();
    }

    @SmallTest
    @UiThreadTest
    @Test(expected = IllegalStateException.class)
    public void testRequireViewWithoutView() {
        StrictFragment fragment1 = new StrictFragment();
        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment1, "fragment")
                .commitNow();
        fragment1.requireView();
    }

    @SmallTest
    @UiThreadTest
    @Test
    public void testOnCreateOrder() throws Throwable {
        OrderFragment fragment1 = new OrderFragment();
        OrderFragment fragment2 = new OrderFragment();
        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.content, fragment1)
                .add(R.id.content, fragment2)
                .commitNow();
        assertEquals(0, fragment1.createOrder);
        assertEquals(1, fragment2.createOrder);
    }

    @LargeTest
    @Test
    @SdkSuppress(minSdkVersion = 16) // waitForHalfFadeIn requires API 16
    public void testChildFragmentManagerGone() throws Throwable {
        final FragmentA fragmentA = new FragmentA();
        final FragmentB fragmentB = new FragmentB();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getSupportFragmentManager().beginTransaction()
                        .add(R.id.content, fragmentA)
                        .commitNow();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.long_fade_in, R.anim.long_fade_out,
                                R.anim.long_fade_in, R.anim.long_fade_out)
                        .replace(R.id.content, fragmentB)
                        .addToBackStack(null)
                        .commit();
            }
        });
        // Wait for the middle of the animation
        waitForHalfFadeIn(fragmentB);

        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.long_fade_in, R.anim.long_fade_out,
                                R.anim.long_fade_in, R.anim.long_fade_out)
                        .replace(R.id.content, fragmentA)
                        .addToBackStack(null)
                        .commit();
            }
        });
        // Wait for the middle of the animation
        waitForHalfFadeIn(fragmentA);
        FragmentTestUtil.popBackStackImmediate(mActivityRule);

        // Wait for the middle of the animation
        waitForHalfFadeIn(fragmentB);
        FragmentTestUtil.popBackStackImmediate(mActivityRule);
    }

    @RequiresApi(16) // ViewTreeObserver.OnDrawListener was added in API 16
    private void waitForHalfFadeIn(Fragment fragment) throws Throwable {
        if (fragment.getView() == null) {
            FragmentTestUtil.waitForExecution(mActivityRule);
        }
        final View view = fragment.requireView();
        final Animation animation = view.getAnimation();
        if (animation == null || animation.hasEnded()) {
            // animation has already completed
            return;
        }

        final long startTime = animation.getStartTime();
        if (view.getDrawingTime() > animation.getStartTime()) {
            return; // We've already done at least one frame
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                if (animation.hasEnded() || view.getDrawingTime() > startTime) {
                    final ViewTreeObserver.OnDrawListener onDrawListener = this;
                    latch.countDown();
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            viewTreeObserver.removeOnDrawListener(onDrawListener);
                        }
                    });
                }
            }
        };
        viewTreeObserver.addOnDrawListener(listener);
        latch.await(5, TimeUnit.SECONDS);
    }

    @MediumTest
    @UiThreadTest
    @Test
    public void testViewOrder() throws Throwable {
        FragmentA fragmentA = new FragmentA();
        FragmentB fragmentB = new FragmentB();
        FragmentC fragmentC = new FragmentC();
        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.content, fragmentA)
                .add(R.id.content, fragmentB)
                .add(R.id.content, fragmentC)
                .commitNow();
        ViewGroup content = (ViewGroup) mActivity.findViewById(R.id.content);
        assertEquals(3, content.getChildCount());
        assertNotNull(content.getChildAt(0).findViewById(R.id.textA));
        assertNotNull(content.getChildAt(1).findViewById(R.id.textB));
        assertNotNull(content.getChildAt(2).findViewById(R.id.textC));
    }

    @SmallTest
    @UiThreadTest
    @Test
    public void testRequireParentFragment() {
        StrictFragment parentFragment = new StrictFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(parentFragment, "parent")
                .commitNow();

        FragmentManager childFragmentManager = parentFragment.getChildFragmentManager();
        StrictFragment childFragment = new StrictFragment();
        childFragmentManager.beginTransaction()
                .add(childFragment, "child")
                .commitNow();
        assertThat(childFragment.requireParentFragment())
                .isSameAs(parentFragment);
    }

    @SmallTest
    @Test
    public void requireMethodsThrowsWhenNotAttached() {
        Fragment fragment = new Fragment();
        try {
            fragment.requireContext();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            fragment.requireActivity();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            fragment.requireHost();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            fragment.requireParentFragment();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            fragment.requireView();
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            fragment.requireFragmentManager();
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    public static class OrderFragment extends Fragment {
        private static AtomicInteger sOrder = new AtomicInteger();
        public int createOrder = -1;

        public OrderFragment() {}

        @Override
        public void onCreate(Bundle savedInstanceState) {
            createOrder = sOrder.getAndIncrement();
            super.onCreate(savedInstanceState);
        }
    }

    public static class FragmentA extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_a, container, false);
        }
    }

    public static class FragmentB extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_b, container, false);
        }
    }

    public static class FragmentC extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_c, container, false);
        }
    }
}
