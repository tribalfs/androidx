/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.core.impl.converters;

import androidx.appactions.interaction.capabilities.core.impl.exceptions.StructConversionException;

/** Contains function interfaces that throw checked exceptions. */
public final class CheckedInterfaces {

    private CheckedInterfaces() {
    }

    /** A BiConsumer interface that can throw StructConversionException. */
    @FunctionalInterface
    interface BiConsumer<T, U> {
        void accept(T t, U u) throws StructConversionException;
    }

    /**
     * A Function interface that can throw StructConversionException.
     *
     * @param <T>
     * @param <R>
     */
    @FunctionalInterface
    interface Function<T, R> {
        R apply(T t) throws StructConversionException;
    }

    /** A Consumer interface that can throw StructConversionException. */
    @FunctionalInterface
    interface Consumer<T> {
        void accept(T t) throws StructConversionException;
    }
}
