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

package android.arch.lifecycle;

import static android.arch.lifecycle.Lifecycle.Event.ON_ANY;
import static android.arch.lifecycle.Lifecycle.Event.ON_CREATE;
import static android.arch.lifecycle.Lifecycle.Event.ON_DESTROY;
import static android.arch.lifecycle.Lifecycle.Event.ON_PAUSE;
import static android.arch.lifecycle.Lifecycle.Event.ON_RESUME;
import static android.arch.lifecycle.Lifecycle.Event.ON_START;
import static android.arch.lifecycle.Lifecycle.Event.ON_STOP;
import static android.arch.lifecycle.Lifecycle.State.CREATED;
import static android.arch.lifecycle.Lifecycle.State.INITIALIZED;
import static android.arch.lifecycle.Lifecycle.State.RESUMED;
import static android.arch.lifecycle.Lifecycle.State.STARTED;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReflectiveGenericLifecycleObserverTest {
    private LifecycleOwner mOwner;
    private Lifecycle mLifecycle;

    @Before
    public void initMocks() {
        mOwner = mock(LifecycleOwner.class);
        mLifecycle = mock(Lifecycle.class);
        when(mOwner.getLifecycle()).thenReturn(mLifecycle);
    }

    @Test
    public void anyState() {
        AnyStateListener obj = mock(AnyStateListener.class);
        ReflectiveGenericLifecycleObserver observer = new ReflectiveGenericLifecycleObserver(obj);
        when(mLifecycle.getCurrentState()).thenReturn(STARTED);
        observer.onStateChanged(mOwner, ON_CREATE);
        verify(obj).onAnyState();
        reset(obj);

        observer.onStateChanged(mOwner, ON_START);
        verify(obj).onAnyState();
        reset(obj);

        observer.onStateChanged(mOwner, ON_RESUME);
        verify(obj).onAnyState();
        reset(obj);

        observer.onStateChanged(mOwner, ON_PAUSE);
        verify(obj).onAnyState();
        reset(obj);

        observer.onStateChanged(mOwner, ON_STOP);
        verify(obj).onAnyState();
        reset(obj);

        observer.onStateChanged(mOwner, ON_DESTROY);
        verify(obj).onAnyState();
        reset(obj);
    }

    private static class AnyStateListener implements LifecycleObserver {
        @OnLifecycleEvent(ON_ANY)
        void onAnyState() {

        }
    }

    @Test
    public void singleMethod() {
        CreatedStateListener obj = mock(CreatedStateListener.class);
        ReflectiveGenericLifecycleObserver observer = new ReflectiveGenericLifecycleObserver(obj);
        when(mLifecycle.getCurrentState()).thenReturn(CREATED);
        observer.onStateChanged(mOwner, ON_CREATE);
        verify(obj).onCreated();
        verify(obj).onCreated(mOwner);
        verify(obj).onCreated(mOwner, ON_CREATE);
    }

    private static class CreatedStateListener implements LifecycleObserver {
        @OnLifecycleEvent(ON_CREATE)
        void onCreated() {

        }
        @SuppressWarnings("UnusedParameters")
        @OnLifecycleEvent(ON_CREATE)
        void onCreated(LifecycleOwner provider) {

        }
        @SuppressWarnings("UnusedParameters")
        @OnLifecycleEvent(ON_CREATE)
        void onCreated(LifecycleOwner provider, Lifecycle.Event event) {

        }
    }

    @Test
    public void eachEvent() {
        AllMethodsListener obj = mock(AllMethodsListener.class);
        ReflectiveGenericLifecycleObserver observer = new ReflectiveGenericLifecycleObserver(obj);
        when(mLifecycle.getCurrentState()).thenReturn(CREATED);

        observer.onStateChanged(mOwner, ON_CREATE);
        verify(obj).created();
        reset(obj);

        when(mLifecycle.getCurrentState()).thenReturn(STARTED);
        observer.onStateChanged(mOwner, ON_START);
        verify(obj).started();
        reset(obj);

        when(mLifecycle.getCurrentState()).thenReturn(RESUMED);
        observer.onStateChanged(mOwner, ON_RESUME);
        verify(obj).resumed();
        reset(obj);

        when(mLifecycle.getCurrentState()).thenReturn(STARTED);
        observer.onStateChanged(mOwner, ON_PAUSE);
        verify(obj).paused();
        reset(obj);

        when(mLifecycle.getCurrentState()).thenReturn(CREATED);
        observer.onStateChanged(mOwner, ON_STOP);
        verify(obj).stopped();
        reset(obj);

        when(mLifecycle.getCurrentState()).thenReturn(INITIALIZED);
        observer.onStateChanged(mOwner, ON_DESTROY);
        verify(obj).destroyed();
        reset(obj);
    }


    private static class AllMethodsListener implements LifecycleObserver {
        @OnLifecycleEvent(ON_CREATE)
        void created() {}

        @OnLifecycleEvent(ON_START)
        void started() {}

        @OnLifecycleEvent(ON_RESUME)
        void resumed() {}

        @OnLifecycleEvent(ON_PAUSE)
        void paused() {}

        @OnLifecycleEvent(ON_STOP)
        void stopped() {}

        @OnLifecycleEvent(ON_DESTROY)
        void destroyed() {}
    }
}
