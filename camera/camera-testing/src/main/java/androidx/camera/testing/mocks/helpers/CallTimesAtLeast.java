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

package androidx.camera.testing.mocks.helpers;

/**
 * Utility for defining the number of invocations allowed while testing fake class methods.
 */
public class CallTimesAtLeast extends CallTimes {
    /**
     * Creates a new instance of {@link CallTimesAtLeast} with the given parameter.
     *
     * @param times the minimum number of invocations that should be occurring
     */
    public CallTimesAtLeast(int times) {
        super(times);
    }

    /**
     * Checks if the number of invocation is at least {@link #mTimes}.
     *
     * @param actualCallCount the occurred number of invocations
     * @return {@code true} if the number of invocations is at least the times specified,
     *          {@code false} otherwise
     */
    @Override
    public boolean isSatisfied(int actualCallCount) {
        return actualCallCount >= mTimes;
    }
}
