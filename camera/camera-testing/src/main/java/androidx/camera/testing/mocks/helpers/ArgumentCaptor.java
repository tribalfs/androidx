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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for capturing arguments, usually in fake class method invocations for testing.
 *
 * @param <T> the type of the arguments to capture
 */
public class ArgumentCaptor<T> {
    private final List<T> mArguments = new ArrayList<>();
    private ArgumentMatcher<T> mArgumentMatcher;

    /**
     * Creates a new instance of {@link ArgumentCaptor}.
     */
    public ArgumentCaptor() {}

    /**
     * Creates a new instance of {@link ArgumentCaptor} with the given parameter.
     *
     * @param argumentMatcher specifies the matching criteria for capturing
     */
    public ArgumentCaptor(@NonNull ArgumentMatcher<T> argumentMatcher) {
        mArgumentMatcher = argumentMatcher;
    }

    /**
     * Returns the last value captured, {@code null} if no value has been captured yet
     */
    @Nullable
    public T getValue() {
        if (mArguments.size() == 0) {
            return null;
        }

        return mArguments.get(mArguments.size() - 1);
    }

    /**
     * Adds arguments to capture list according to argument matching rule (if exists).
     *
     * @param argumentList the list of arguments to capture
     */
    public void setArguments(@NonNull List<T> argumentList) {
        for (T argument : argumentList) {
            if (mArgumentMatcher == null || mArgumentMatcher.matches(argument)) {
                mArguments.add(argument);
            }
        }
    }
}
