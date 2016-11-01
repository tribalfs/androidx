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

package com.android.support.lifecycle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LifecycleRegistryTest {
    private LifecycleProvider mLifecycleProvider;
    private Lifecycle mLifecycle;
    private LifecycleRegistry mRegistry;
    @Before
    public void init() {
        mLifecycleProvider = mock(LifecycleProvider.class);
        mLifecycle = mock(Lifecycle.class);
        when(mLifecycleProvider.getLifecycle()).thenReturn(mLifecycle);
        mRegistry = new LifecycleRegistry(mLifecycleProvider, Lifecycle.INITIALIZED);
    }
    @Test
    public void addRemove() {
        LifecycleObserver observer = mock(LifecycleObserver.class);
        mRegistry.addObserver(observer);
        assertThat(mRegistry.size(), is(1));
        mRegistry.removeObserver(observer);
        assertThat(mRegistry.size(), is(0));
    }

    @Test
    public void addGenericAndObserve() {
        GenericLifecycleObserver generic = mock(GenericLifecycleObserver.class);
        mRegistry.addObserver(generic);
        setState(Lifecycle.CREATED);
        verify(generic).onStateChanged(mLifecycleProvider, Lifecycle.INITIALIZED);
        reset(generic);
        setState(Lifecycle.CREATED);
        verify(generic, never()).onStateChanged(mLifecycleProvider, Lifecycle.INITIALIZED);
    }

    @Test
    public void addRegularClass() {
        TestObserver testObserver = mock(TestObserver.class);
        mRegistry.addObserver(testObserver);
        setState(Lifecycle.STARTED);
        verify(testObserver, never()).onStopped();
        setState(Lifecycle.STOPPED);
        verify(testObserver).onStopped();
    }

    @Test
    public void add2RemoveOne() {
        TestObserver observer1 = mock(TestObserver.class);
        TestObserver observer2 = mock(TestObserver.class);
        TestObserver observer3 = mock(TestObserver.class);
        mRegistry.addObserver(observer1);
        mRegistry.addObserver(observer2);
        mRegistry.addObserver(observer3);

        setState(Lifecycle.STOPPED);

        verify(observer1).onStopped();
        verify(observer2).onStopped();
        verify(observer3).onStopped();
        reset(observer1, observer2, observer3);

        mRegistry.removeObserver(observer2);
        setState(Lifecycle.PAUSED);

        setState(Lifecycle.STOPPED);
        verify(observer1).onStopped();
        verify(observer2, never()).onStopped();
        verify(observer3).onStopped();
    }

    @Test
    public void removeWhileTraversing() {
        final TestObserver observer2 = mock(TestObserver.class);
        TestObserver observer1 = spy(new TestObserver() {
            @Override
            public void onStopped() {
                mRegistry.removeObserver(observer2);
            }
        });
        mRegistry.addObserver(observer1);
        mRegistry.addObserver(observer2);
        setState(Lifecycle.STOPPED);
        verify(observer2, never()).onStopped();
        verify(observer1).onStopped();
    }

    private void setState(@Lifecycle.State int state) {
        when(mLifecycle.getCurrentState()).thenReturn(state);
        mRegistry.setCurrentState(state);
    }

    private interface TestObserver extends LifecycleObserver {
        @OnState(Lifecycle.STOPPED)
        void onStopped();
    }
}
