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

import android.support.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An internal implementation of {@link GenericLifecycleObserver} that relies on reflection.
 */
@SuppressWarnings("unused")
class ReflectiveGenericLifecycleObserver implements GenericLifecycleObserver {
    private final Object mWrapped;
    private final CallbackInfo mInfo;
    @SuppressWarnings("WeakerAccess")
    static final Map<Class, CallbackInfo> sInfoCache = new HashMap<>();

    ReflectiveGenericLifecycleObserver(Object wrapped) {
        mWrapped = wrapped;
        mInfo = getInfo(mWrapped.getClass());
    }

    @Override
    public void onStateChanged(LifecycleProvider source, @Lifecycle.Event int event) {
        final int state = source.getLifecycle().getCurrentState();
        invokeCallbacks(mInfo, source, state, event);
    }

    @SuppressWarnings("ConstantConditions")
    private void invokeCallbacks(CallbackInfo info, LifecycleProvider source,
            @Lifecycle.State int state, @Lifecycle.Event int event) {
        if ((info.mEvents & event) != 0) {
            for (int i = info.mMethodReferences.size() - 1; i >= 0; i--) {
                MethodReference reference = info.mMethodReferences.get(i);
                invokeCallback(reference, source, state, event);
            }
        }
        // TODO prevent duplicate calls into the same method. Preferably while parsing
        if (info.mSuper != null) {
            invokeCallbacks(info.mSuper, source, state, event);
        }
        if (info.mInterfaces != null) {
            final int size = info.mInterfaces.size();
            for (int i = 0; i < size; i++) {
                invokeCallbacks(info.mInterfaces.get(i), source, state, event);
            }
        }
    }

    private void invokeCallback(MethodReference reference, LifecycleProvider source,
            @Lifecycle.State int state, @Lifecycle.Event  int event) {
        if ((reference.mEvents & event) != 0) {
            try {
                switch (reference.mCallType) {
                    case CALL_TYPE_NO_ARG:
                        reference.mMethod.invoke(mWrapped);
                        break;
                    case CALL_TYPE_PROVIDER:
                        reference.mMethod.invoke(mWrapped, source);
                        break;
                    case CALL_TYPE_PROVIDER_WITH_EVENT:
                        reference.mMethod.invoke(mWrapped, source, event);
                        break;
                }
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Object getReceiver() {
        return mWrapped;
    }

    private static CallbackInfo getInfo(Class klass) {
        CallbackInfo existing = sInfoCache.get(klass);
        if (existing != null) {
            return existing;
        }
        existing = createInfo(klass);
        return existing;
    }

    private static CallbackInfo createInfo(Class klass) {
        Method[] methods = klass.getDeclaredMethods();
        List<MethodReference> methodReferences = null;
        int allEvents = 0;
        for (Method method : methods) {
            OnLifecycleEvent annotation = method.getAnnotation(OnLifecycleEvent.class);
            if (annotation == null) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            int callType = CALL_TYPE_NO_ARG;
            if (params.length > 0) {
                callType = CALL_TYPE_PROVIDER;
                if (!params[0].isAssignableFrom(LifecycleProvider.class)) {
                    throw new IllegalArgumentException(
                            "invalid parameter type. Must be one and instanceof LifecycleProvider");
                }
            }
            if (params.length > 1) {
                callType = CALL_TYPE_PROVIDER_WITH_EVENT;
                if (!params[1].isAssignableFrom(int.class)) {
                    throw new IllegalArgumentException(
                            "invalid parameter type. second arg must be an event");
                }
            }
            if (params.length > 2) {
                throw new IllegalArgumentException("cannot have more than 2 params");
            }
            if (methodReferences == null) {
                methodReferences = new ArrayList<>();
            }
            methodReferences.add(new MethodReference(annotation.value(), callType, method));
            allEvents |= annotation.value();
        }
        CallbackInfo info = new CallbackInfo(allEvents, methodReferences);
        sInfoCache.put(klass, info);
        Class superclass = klass.getSuperclass();
        if (superclass != null) {
            info.mSuper = getInfo(superclass);
        }
        Class[] interfaces = klass.getInterfaces();
        for (Class intrfc : interfaces) {
            CallbackInfo interfaceInfo = getInfo(intrfc);
            if (interfaceInfo.mEvents != 0) {
                if (info.mInterfaces == null) {
                    info.mInterfaces = new ArrayList<>();
                }
                info.mInterfaces.add(interfaceInfo);
            }
        }
        return info;
    }

    @SuppressWarnings("WeakerAccess")
    static class CallbackInfo {
        int mEvents;
        @Nullable
        List<MethodReference> mMethodReferences;
        @Nullable
        List<CallbackInfo> mInterfaces;
        @Nullable
        CallbackInfo mSuper;

        CallbackInfo(int events, @Nullable List<MethodReference> methodReferences) {
            mEvents = events;
            mMethodReferences = methodReferences;
        }
    }

    @SuppressWarnings("WeakerAccess")
    static class MethodReference {
        final int mEvents;
        final int mCallType;
        final Method mMethod;

        MethodReference(int events, int callType, Method method) {
            mEvents = events;
            mCallType = callType;
            mMethod = method;
        }
    }

    private static final int CALL_TYPE_NO_ARG = 0;
    private static final int CALL_TYPE_PROVIDER = 1;
    private static final int CALL_TYPE_PROVIDER_WITH_EVENT = 2;
}
