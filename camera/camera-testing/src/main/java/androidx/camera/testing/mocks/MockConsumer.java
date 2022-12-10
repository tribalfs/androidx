/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.testing.mocks;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.testing.mocks.helpers.ArgumentCaptor;
import androidx.camera.testing.mocks.helpers.CallTimes;
import androidx.camera.testing.mocks.helpers.CallTimesAtLeast;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A mock implementation of {@link Consumer} to verify proper invocations in tests.
 *
 * @param <T> the parameter type to be consumed
 */
// TODO(b/239752223): We should change all the mock consumer to this manual mock for
//  consistency, or try to figure out why this error only happens on certain tests.
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public class MockConsumer<T> implements Consumer<T> {

    private CountDownLatch mLatch;

    private final List<T> mEventList = new ArrayList<>();
    private final Map<Integer, Boolean> mIsEventVerifiedByIndex = new HashMap<>();
    private int mIndexLastVerifiedInOrder = -1;

    private Class<?> mClassTypeToVerify;
    private CallTimes mCallTimes;
    private boolean mInOrder = false;

    private int getMatchingEventCount() {
        int count = 0;
        int startIndex = mInOrder ? mIndexLastVerifiedInOrder + 1 : 0;
        for (int i = startIndex; i < mEventList.size(); i++) {
            if (mClassTypeToVerify.isInstance(mEventList.get(i))) {
                count++;
            }
        }
        return count;
    }

    private int getLastVerifiedEventInOrder() {
        int count = 0;
        for (int i = mIndexLastVerifiedInOrder + 1; i < mEventList.size(); i++) {
            if (mClassTypeToVerify.isInstance(mEventList.get(i))) {
                count++;
            }

            if (mCallTimes.isSatisfied(count)) {
                return i;
            }
        }

        return -1;
    }

    private void markMatchingEventsAsVerified() {
        if (mInOrder) { // no need to mark for inOrder verifications
            return;
        }

        for (int i = 0; i < mEventList.size(); i++) {
            if (mClassTypeToVerify.isInstance(mEventList.get(i))) {
                mIsEventVerifiedByIndex.put(i, true);
            }
        }
    }

    private boolean isVerified() {
        if (mClassTypeToVerify == null || mCallTimes == null) {
            return false;
        }

        return mCallTimes.isSatisfied(getMatchingEventCount());
    }

    /**
     * Verifies if {@link #accept} method was invoked properly during test.
     *
     * @param captor the argument captor to record the arguments passed to {@link #accept} method
     *               matching the provided {@code classType}
     * @see #verifyAcceptCall(Class, boolean, long, CallTimes)
     */
    public void verifyAcceptCall(@NonNull Class<?> classType, boolean inOrder,
            @NonNull CallTimes callTimes, @Nullable ArgumentCaptor<T> captor) {
        mClassTypeToVerify = classType;
        mCallTimes = callTimes;
        mInOrder = inOrder;

        assertWithMessage(
                "accept() called " + getMatchingEventCount() + " time(s) with "
                        + classType.getSimpleName() + (inOrder ? " in order" : "") + ", expected "
                        + (callTimes instanceof CallTimesAtLeast ? " at least " : "")
                        + callTimes.getTimes() + " times"
        ).that(isVerified()).isTrue();

        markMatchingEventsAsVerified();

        if (inOrder) {
            mIndexLastVerifiedInOrder = getLastVerifiedEventInOrder();
        }

        if (captor != null) {
            captor.setArguments(mEventList);
        }
    }

    /**
     * Verifies if {@link #accept} method was invoked properly during test.
     *
     * <p>{@code callTimes} defaults to {@code new CallTimes(1)} which ensures that
     * {@link #accept} method was invoked exactly once with given verification parameters.
     *
     * @see #verifyAcceptCall(Class, boolean, long, CallTimes)
     */
    public void verifyAcceptCall(@NonNull Class<?> classType, boolean inOrder,
            long timeoutInMillis) {
        verifyAcceptCall(classType, inOrder, timeoutInMillis, new CallTimes(1));
    }

    /**
     * Verifies if {@link #accept} method was invoked properly during test.
     *
     * <p>Usually invoked from a method with {@link org.junit.Test} annotation.
     *
     * @param classType       the class type to verify for the parameter of {@link #accept(Object)}
     *                        method, this is checked with {@link Class#isInstance(Object)} method
     * @param inOrder         the {@link #verifyAcceptCall} method invocations with {@code inOrder =
     *                        true} are chained together to make sure they were in order
     * @param timeoutInMillis the time limit to wait for asynchronous operation after which
     *                        {@link junit.framework.AssertionFailedError} is thrown
     * @param callTimes       the condition for how many times {@link #accept} method should be
     *                        called
     */
    public void verifyAcceptCall(@NonNull Class<?> classType, boolean inOrder,
            long timeoutInMillis, @NonNull CallTimes callTimes) {
        mClassTypeToVerify = classType;
        mCallTimes = callTimes;
        mInOrder = inOrder;

        if (!isVerified()) {
            mLatch = new CountDownLatch(1);

            try {
                assertWithMessage(
                        "Test failed for a timeout of " + timeoutInMillis + " ms"
                ).that(mLatch.await(timeoutInMillis, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertWithMessage(
                    "accept() called " + getMatchingEventCount() + " time(s) with "
                            + classType.getSimpleName()
                            + (mInOrder ? " in order" : "") + ", expected "
                            + (callTimes instanceof CallTimesAtLeast ? " at least " : "")
                            + callTimes.getTimes() + " times"
            ).that(isVerified()).isTrue();

            markMatchingEventsAsVerified();

            if (inOrder) {
                mIndexLastVerifiedInOrder = getLastVerifiedEventInOrder();
            }

            mLatch = null;
        }
    }

    /**
     * Verifies if there are {@link #accept} calls that haven't been verified.
     *
     * @param inOrder {@code true} to only verify the events after the last in order
     *                verification, {@code false} otherwise.
     */
    public void verifyNoMoreAcceptCalls(boolean inOrder) {
        if (inOrder) {
            assertWithMessage(
                    "There are extra accept() calls after the last in-order verification"
            ).that(mIndexLastVerifiedInOrder).isEqualTo(mEventList.size() - 1);
        } else {
            for (int i = 0; i < mEventList.size(); i++) {
                assertWithMessage(
                        "There are extra accept() calls after the last verification"
                                + "\nFirst such call is with "
                                + mEventList.get(i).getClass().getSimpleName() + " event"
                ).that(mIsEventVerifiedByIndex.get(i)).isTrue();
            }
        }
    }

    /**
     * Clears the previous invocations of {@link #accept} method.
     */
    public void clearAcceptCalls() {
        mEventList.clear();
        mIsEventVerifiedByIndex.clear();
        mIndexLastVerifiedInOrder = -1;
    }

    @Override
    public void accept(T event) {
        mEventList.add(event);

        if (mLatch != null && isVerified()) {
            mLatch.countDown();
        }
    }
}
