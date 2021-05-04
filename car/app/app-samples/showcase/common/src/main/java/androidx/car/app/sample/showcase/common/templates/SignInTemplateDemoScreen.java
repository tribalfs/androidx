/*
 * Copyright (C) 2021 The Android Open Source Project
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

package androidx.car.app.sample.showcase.common.templates;

import static androidx.car.app.CarToast.LENGTH_LONG;
import static androidx.car.app.CarToast.LENGTH_SHORT;

import android.graphics.Color;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.OnInputCompletedListener;
import androidx.car.app.model.ParkedOnlyOnClickListener;
import androidx.car.app.model.Template;
import androidx.car.app.model.signin.InputSignInMethod;
import androidx.car.app.model.signin.PinSignInMethod;
import androidx.car.app.model.signin.ProviderSignInMethod;
import androidx.car.app.model.signin.SignInTemplate;
import androidx.car.app.sample.showcase.common.R;
import androidx.car.app.sample.showcase.common.common.Utils;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.core.graphics.drawable.IconCompat;

/** A screen that demonstrates the sign-in template. */
public class SignInTemplateDemoScreen extends Screen {
    private enum State {
        USERNAME,
        PASSWORD,
        PIN,
        PROVIDER,
        SIGNED_IN,
    }

    private static final String EMAIL_REGEXP = "^(.+)@(.+)$";
    private static final String EXPECTED_PASSWORD = "password";

    // package private to avoid synthetic accessor
    State mState = State.USERNAME;
    private String mUsername = null;
    private String mErrorMessage;

    private final CharSequence mAdditionalText = Utils.clickable("Please review our terms of "
            + "service", 18, 16,
            () -> getScreenManager().push(new LongMessageTemplateDemoScreen(getCarContext())));

    private final Action mProviderSignInAction = new Action.Builder()
            .setTitle("Google Sign-In")
            .setOnClickListener(ParkedOnlyOnClickListener.create(() -> {
                mState = State.PROVIDER;
                invalidate();
            }))
            .build();

    private final Action mPinSignInAction = new Action.Builder()
            .setTitle("Use PIN")
            .setOnClickListener(ParkedOnlyOnClickListener.create(() -> {
                mState = State.PIN;
                invalidate();
            }))
            .build();

    public SignInTemplateDemoScreen(@NonNull CarContext carContext) {
        super(carContext);

        // Handle back pressed events manually, as we use them to navigate between templates within
        // the same screen.
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mState == State.USERNAME || mState == State.SIGNED_IN) {
                    getScreenManager().pop();
                } else {
                    mState = State.USERNAME;
                    invalidate();
                }
            }
        };
        carContext.getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (getCarContext().getCarAppApiLevel() < CarAppApiLevels.LEVEL_2) {
            return new MessageTemplate.Builder("Your host doesn't support Sign In template")
                    .setTitle("Incompatible host")
                    .setHeaderAction(Action.BACK)
                    .build();
        }
        switch (mState) {
            case USERNAME:
                return getUsernameSignInTemplate();
            case PASSWORD:
                return getPasswordSignInTemplate();
            case PIN:
                return getPinSignInTemplate();
            case PROVIDER:
                return getProviderSignInTemplate();
            case SIGNED_IN:
                return getSignInCompletedMessageTemplate();
        }
        throw new IllegalStateException("Invalid state: " + mState);
    }

    private Template getUsernameSignInTemplate() {
        OnInputCompletedListener listener = text -> {
            // Mocked username validation
            if (!text.matches(EMAIL_REGEXP)) {
                mErrorMessage = "Invalid username";
            } else {
                mErrorMessage = "";
                mUsername = text;
                mState = State.PASSWORD;
            }
            invalidate();
        };
        InputSignInMethod.Builder builder = new InputSignInMethod.Builder(listener)
                .setHint("Email")
                .setKeyboardType(InputSignInMethod.KEYBOARD_EMAIL);
        if (mErrorMessage != null) {
            builder.setErrorMessage(mErrorMessage);
        }
        if (mUsername != null) {
            builder.setDefaultValue(mUsername);
        }
        InputSignInMethod signInMethod = builder.build();

        return new SignInTemplate.Builder(signInMethod)
                .addAction(mProviderSignInAction)
                .addAction(mPinSignInAction)
                .setTitle("Sign in with username and password")
                .setInstructions("Enter your credentials")
                .setHeaderAction(Action.BACK)
                .setAdditionalText(mAdditionalText)
                .setActionStrip(
                        new ActionStrip.Builder()
                                .addAction(
                                        new Action.Builder()
                                                .setTitle("Settings")
                                                .setOnClickListener(
                                                        () ->
                                                                CarToast.makeText(
                                                                        getCarContext(),
                                                                        "Clicked Settings",
                                                                        LENGTH_LONG)
                                                                        .show())
                                                .build())
                                .build())
                .build();
    }

    private Template getPasswordSignInTemplate() {
        OnInputCompletedListener listener = text -> {
            // Mocked password validation
            if (!EXPECTED_PASSWORD.equals(text)) {
                mErrorMessage = "Invalid password";
            } else {
                mErrorMessage = "";
                mState = State.SIGNED_IN;
            }
            invalidate();
        };
        InputSignInMethod.Builder builder = new InputSignInMethod.Builder(listener)
                .setHint("Password")
                .setInputType(InputSignInMethod.INPUT_TYPE_PASSWORD);
        if (mErrorMessage != null) {
            builder.setErrorMessage(mErrorMessage);
        }
        InputSignInMethod signInMethod = builder.build();

        return new SignInTemplate.Builder(signInMethod)
                .addAction(mProviderSignInAction)
                .addAction(mPinSignInAction)
                .setTitle("Sign in with username and password")
                .setInstructions("Enter your credentials")
                .setHeaderAction(Action.BACK)
                .setAdditionalText(mAdditionalText)
                .build();
    }

    private Template getPinSignInTemplate() {
        PinSignInMethod pinSignInMethod = new PinSignInMethod.Builder("123456789abc"
                .toString().toUpperCase())
                .build();

        return new SignInTemplate.Builder(pinSignInMethod)
                .setTitle("Sign in with PIN")
                .setInstructions("Type this PIN in your phone")
                .setHeaderAction(Action.BACK)
                .setAdditionalText(mAdditionalText)
                .build();
    }

    private Template getProviderSignInTemplate() {
        IconCompat providerIcon = IconCompat.createWithResource(getCarContext(),
                R.drawable.ic_googleg);
        CarColor noTint = CarColor.createCustom(Color.TRANSPARENT, Color.TRANSPARENT);

        ProviderSignInMethod providerSignInMethod = new ProviderSignInMethod.Builder(
                new Action.Builder()
                        .setTitle(Utils.colorize("Sign in with Google",
                                CarColor.createCustom(Color.BLACK, Color.BLACK), 0, 19))
                        .setBackgroundColor(CarColor.createCustom(Color.WHITE, Color.WHITE))
                        .setIcon(new CarIcon.Builder(providerIcon)
                                .setTint(noTint)
                                .build())
                        .setOnClickListener(ParkedOnlyOnClickListener.create(
                                () -> CarToast.makeText(
                                        getCarContext(),
                                        "Sign-in with Google starts here",
                                        LENGTH_SHORT)
                                        .show()))
                        .build()
        ).build();

        return new SignInTemplate.Builder(providerSignInMethod)
                .setTitle("Sign in with Google")
                .setInstructions("Use this button to complete your Google sign-in")
                .setHeaderAction(Action.BACK)
                .setAdditionalText(mAdditionalText)
                .build();
    }

    private MessageTemplate getSignInCompletedMessageTemplate() {
        return new MessageTemplate.Builder("You are signed in!")
                .setTitle("Sign in completed")
                .setHeaderAction(Action.BACK)
                .addAction(new Action.Builder()
                        .setTitle("Sign out")
                        .setOnClickListener(() -> {
                            mState = State.USERNAME;
                            invalidate();
                        })
                        .build())
                .build();
    }
}
