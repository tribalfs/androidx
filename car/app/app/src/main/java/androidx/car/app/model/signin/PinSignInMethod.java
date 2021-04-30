/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.car.app.model.signin;

import static java.util.Objects.requireNonNull;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.annotations.RequiresCarApi;

import java.util.Objects;

/**
 * A {@link SignInTemplate.SignInMethod} that presents a PIN or activation code that the user can
 * use to sign-in.
 */
@RequiresCarApi(2)
public final class PinSignInMethod implements SignInTemplate.SignInMethod {
    /** Maximum length, in characters, for a PIN. */
    private static final int MAX_PIN_LENGTH = 12;

    @Keep
    @Nullable
    private final String mPin;

    /**
     * Returns the PIN or activation code to present to the user or {@code null} if not set.
     *
     * @see Builder#Builder(String)
     */
    @NonNull
    public String getPin() {
        return requireNonNull(mPin);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof PinSignInMethod)) {
            return false;
        }

        PinSignInMethod that = (PinSignInMethod) other;
        return Objects.equals(mPin, that.mPin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPin);
    }

    PinSignInMethod(Builder builder) {
        mPin = builder.mPin;
    }

    /** Constructs an empty instance, used by serialization code. */
    private PinSignInMethod() {
        mPin = null;
    }

    /** A builder of {@link PinSignInMethod}. */
    public static final class Builder {
        final String mPin;

        /**
         * Returns a {@link PinSignInMethod} instance.
         */
        @NonNull
        public PinSignInMethod build() {
            return new PinSignInMethod(this);
        }

        /**
         * Returns a {@link PinSignInMethod.Builder} instance.
         *
         * <p>The provided pin must be no more than 12 characters long. To facilitate typing this
         * code, it is recommended restricting the string to a limited set (for example, numbers,
         * upper-case letters, hexadecimal, etc.).
         *
         * @param pin the PIN to display is empty.
         * @throws IllegalArgumentException if {@code pin} is empty or longer than 12 characters.
         * @throws NullPointerException     if {@code pin} is {@code null}
         */
        public Builder(@NonNull String pin) {
            int pinLength = pin.length();
            if (pinLength == 0) {
                throw new IllegalArgumentException("PIN must not be empty");
            }
            if (pinLength > MAX_PIN_LENGTH) {
                throw new IllegalArgumentException(
                        "PIN must not be longer than " + MAX_PIN_LENGTH + " characters");
            }
            mPin = pin;
        }
    }
}
