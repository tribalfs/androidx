/* This file is auto-generated from GuidedStepFragmentTest.java.  DO NOT MODIFY. */

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

import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.testutils.PollingCheck;
import android.view.ViewGroup;
import android.view.View;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import android.support.test.espresso.Espresso;
import android.support.test.espresso.matcher.RootMatchers;
import android.support.test.espresso.action.ViewActions;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * @hide from javadoc
 */
@RunWith(AndroidJUnit4.class)
public class GuidedStepSupportFragmentTest extends GuidedStepSupportFragmentTestBase {

    @Test
    public void nextAndBack() throws Throwable {
        GuidedStepTestSupportFragment.Provider first = mockProvider("first");
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                List actions = (List) invocation.getArguments()[0];
                actions.add(new GuidedAction.Builder().id(1000).title("OK").build());
                return null;
            }
        }).when(first).onCreateActions(any(List.class), any(Bundle.class));
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                GuidedAction action = (GuidedAction) invocation.getArguments()[0];
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                if (action.getId() == 1000) {
                    GuidedStepSupportFragment.add(obj.getFragmentManager(),
                            new GuidedStepTestSupportFragment("second"));
                }
                return null;
            }
        }).when(first).onGuidedActionClicked(any(GuidedAction.class));

        GuidedStepTestSupportFragment.Provider second = mockProvider("second");

        GuidedStepSupportFragmentTestActivity activity = launchTestActivity("first");
        verify(first, times(1)).onCreate(any(Bundle.class));
        verify(first, times(1)).onCreateGuidance(any(Bundle.class));
        verify(first, times(1)).onCreateActions(any(List.class), any(Bundle.class));
        verify(first, times(1)).onCreateButtonActions(any(List.class), any(Bundle.class));
        verify(first, times(1)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        verify(first, times(1)).onViewStateRestored(any(Bundle.class));
        verify(first, times(1)).onStart();
        verify(first, times(1)).onResume();

        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        verify(first, times(1)).onGuidedActionClicked(any(GuidedAction.class));

        PollingCheck.waitFor(new EnterTransitionFinish(second));
        verify(first, times(1)).onPause();
        verify(first, times(1)).onStop();
        verify(first, times(1)).onDestroyView();
        verify(second, times(1)).onCreate(any(Bundle.class));
        verify(second, times(1)).onCreateGuidance(any(Bundle.class));
        verify(second, times(1)).onCreateActions(any(List.class), any(Bundle.class));
        verify(second, times(1)).onCreateButtonActions(any(List.class), any(Bundle.class));
        verify(second, times(1)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        verify(second, times(1)).onViewStateRestored(any(Bundle.class));
        verify(second, times(1)).onStart();
        verify(second, times(1)).onResume();

        sendKey(KeyEvent.KEYCODE_BACK);

        PollingCheck.waitFor(new EnterTransitionFinish(first));
        verify(second, times(1)).onPause();
        verify(second, times(1)).onStop();
        verify(second, times(1)).onDestroyView();
        verify(second, times(1)).onDestroy();
        verify(first, times(1)).onCreateActions(any(List.class), any(Bundle.class));
        verify(first, times(2)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        verify(first, times(2)).onViewStateRestored(any(Bundle.class));
        verify(first, times(2)).onStart();
        verify(first, times(2)).onResume();

        sendKey(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(new PollingCheck.ActivityDestroy(activity));
        verify(first, times(1)).onDestroy();
        assertTrue(activity.isDestroyed());
    }

    @Test
    public void restoreFragments() throws Throwable {
        GuidedStepTestSupportFragment.Provider first = mockProvider("first");
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                List actions = (List) invocation.getArguments()[0];
                actions.add(new GuidedAction.Builder().id(1000).title("OK").build());
                actions.add(new GuidedAction.Builder().id(1001).editable(true).title("text")
                        .build());
                actions.add(new GuidedAction.Builder().id(1002).editable(true).title("text")
                        .autoSaveRestoreEnabled(false).build());
                return null;
            }
        }).when(first).onCreateActions(any(List.class), any(Bundle.class));
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                GuidedAction action = (GuidedAction) invocation.getArguments()[0];
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                if (action.getId() == 1000) {
                    GuidedStepSupportFragment.add(obj.getFragmentManager(),
                            new GuidedStepTestSupportFragment("second"));
                }
                return null;
            }
        }).when(first).onGuidedActionClicked(any(GuidedAction.class));

        GuidedStepTestSupportFragment.Provider second = mockProvider("second");

        final GuidedStepSupportFragmentTestActivity activity = launchTestActivity("first");
        first.getFragment().findActionById(1001).setTitle("modified text");
        first.getFragment().findActionById(1002).setTitle("modified text");
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        PollingCheck.waitFor(new EnterTransitionFinish(second));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.recreate();
            }
        });
        PollingCheck.waitFor(new EnterTransitionFinish(second));
        verify(first, times(2)).onCreate(any(Bundle.class));
        verify(first, times(1)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        verify(first, times(2)).onCreateActions(any(List.class), any(Bundle.class));
        verify(first, times(1)).onDestroy();
        verify(second, times(2)).onCreate(any(Bundle.class));
        verify(second, times(2)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        verify(second, times(1)).onDestroy();
        assertEquals("modified text", first.getFragment().findActionById(1001).getTitle());
        assertEquals("text", first.getFragment().findActionById(1002).getTitle());

        sendKey(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(new EnterTransitionFinish(first));
        verify(second, times(2)).onPause();
        verify(second, times(2)).onStop();
        verify(second, times(2)).onDestroyView();
        verify(second, times(2)).onDestroy();
        verify(first, times(2)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
    }


    @Test
    public void finishGuidedStepSupportFragment_finishes_activity() throws Throwable {
        GuidedStepTestSupportFragment.Provider first = mockProvider("first");
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                List actions = (List) invocation.getArguments()[0];
                actions.add(new GuidedAction.Builder().id(1001).title("Finish activity").build());
                return null;
            }
        }).when(first).onCreateActions(any(List.class), any(Bundle.class));
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                GuidedAction action = (GuidedAction) invocation.getArguments()[0];
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                if (action.getId() == 1001) {
                    obj.getFragment().finishGuidedStepSupportFragments();
                }
                return null;
            }
        }).when(first).onGuidedActionClicked(any(GuidedAction.class));

        final GuidedStepSupportFragmentTestActivity activity = launchTestActivity("first");

        View viewFinish = first.getFragment().getActionItemView(0);
        assertTrue(viewFinish.hasFocus());
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        PollingCheck.waitFor(new PollingCheck.ActivityDestroy(activity));
        verify(first, times(1)).onDestroy();
    }

    @Test
    public void finishGuidedStepSupportFragment_finishes_fragments() throws Throwable {
        GuidedStepTestSupportFragment.Provider first = mockProvider("first");
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                List actions = (List) invocation.getArguments()[0];
                actions.add(new GuidedAction.Builder().id(1001).title("Finish fragments").build());
                return null;
            }
        }).when(first).onCreateActions(any(List.class), any(Bundle.class));
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                GuidedAction action = (GuidedAction) invocation.getArguments()[0];
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                if (action.getId() == 1001) {
                    obj.getFragment().finishGuidedStepSupportFragments();
                }
                return null;
            }
        }).when(first).onGuidedActionClicked(any(GuidedAction.class));

        final GuidedStepSupportFragmentTestActivity activity = launchTestActivity("first",
                false /*asRoot*/);

        View viewFinish = first.getFragment().getActionItemView(0);
        assertTrue(viewFinish.hasFocus());
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);

        // fragment should be destroyed, activity should not destroyed
        waitOnDestroy(first, 1);
        assertFalse(activity.isDestroyed());
    }

    @Test
    public void subActions() throws Throwable {
        final boolean[] expandSubActionInOnCreateView = new boolean[] {false};
        GuidedStepTestSupportFragment.Provider first = mockProvider("first");
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                if (expandSubActionInOnCreateView[0]) {
                    obj.getFragment().expandAction(obj.getFragment().findActionById(1000), false);
                }
                return null;
            }
        }).when(first).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                List actions = (List) invocation.getArguments()[0];
                List<GuidedAction> subActions = new ArrayList<GuidedAction>();
                subActions.add(new GuidedAction.Builder().id(2000).title("item1").build());
                subActions.add(new GuidedAction.Builder().id(2001).title("item2").build());
                actions.add(new GuidedAction.Builder().id(1000).subActions(subActions)
                        .title("list").build());
                return null;
            }
        }).when(first).onCreateActions(any(List.class), any(Bundle.class));
        doAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) {
                GuidedStepTestSupportFragment.Provider obj = (GuidedStepTestSupportFragment.Provider)
                        invocation.getMock();
                GuidedAction action = (GuidedAction) invocation.getArguments()[0];
                if (action.getId() == 2000) {
                    return true;
                } else if (action.getId() == 2001) {
                    GuidedStepSupportFragment.add(obj.getFragmentManager(),
                            new GuidedStepTestSupportFragment("second"));
                    return false;
                }
                return false;
            }
        }).when(first).onSubGuidedActionClicked(any(GuidedAction.class));

        GuidedStepTestSupportFragment.Provider second = mockProvider("second");

        final GuidedStepSupportFragmentTestActivity activity = launchTestActivity("first");

        // after clicked, it sub actions list should expand
        View viewForList = first.getFragment().getActionItemView(0);
        assertTrue(viewForList.hasFocus());
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        PollingCheck.waitFor(new ExpandTransitionFinish(first));
        assertFalse(viewForList.hasFocus());

        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        ArgumentCaptor<GuidedAction> actionCapture = ArgumentCaptor.forClass(GuidedAction.class);
        verify(first, times(1)).onSubGuidedActionClicked(actionCapture.capture());
        assertEquals(2000, actionCapture.getValue().getId());
        // after clicked a sub action, it sub actions list should close
        PollingCheck.waitFor(new ExpandTransitionFinish(first));
        assertTrue(viewForList.hasFocus());

        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        PollingCheck.waitFor(new ExpandTransitionFinish(first));

        assertFalse(viewForList.hasFocus());
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
        ArgumentCaptor<GuidedAction> actionCapture2 = ArgumentCaptor.forClass(GuidedAction.class);
        verify(first, times(2)).onSubGuidedActionClicked(actionCapture2.capture());
        assertEquals(2001, actionCapture2.getValue().getId());

        PollingCheck.waitFor(new EnterTransitionFinish(second));
        verify(second, times(1)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));

        // test expand sub action when return to first fragment
        expandSubActionInOnCreateView[0] = true;
        sendKey(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(new EnterTransitionFinish(first));
        verify(first, times(2)).onCreateView(any(LayoutInflater.class), any(ViewGroup.class),
                any(Bundle.class), any(View.class));
        assertTrue(first.getFragment().isExpanded());

        sendKey(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(new ExpandTransitionFinish(first));
        assertFalse(first.getFragment().isExpanded());

        sendKey(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(new PollingCheck.ActivityDestroy(activity));
        verify(first, times(1)).onDestroy();
    }
}
