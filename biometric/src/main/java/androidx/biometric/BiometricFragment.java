/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.biometric;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * A fragment that wraps the BiometricPrompt and has the ability to continue authentication across
 * device configuration changes. This class is not meant to be preserved after process death; for
 * security reasons, the BiometricPromptCompat will automatically stop authentication when the
 * activity is no longer in the foreground.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("SyntheticAccessor")
public class BiometricFragment extends Fragment {

    private static final String TAG = "BiometricFragment";

    // Re-set in onAttach
    private Context mContext;

    // Set whenever the support library's authenticate is called.
    private Bundle mBundle;

    // Re-set by the application, through BiometricPromptCompat upon orientation changes.
    @VisibleForTesting
    Executor mClientExecutor;
    @VisibleForTesting
    DialogInterface.OnClickListener mClientNegativeButtonListener;
    @VisibleForTesting
    BiometricPrompt.AuthenticationCallback mClientAuthenticationCallback;

    // Set once and retained.
    private BiometricPrompt.CryptoObject mCryptoObject;
    private CharSequence mNegativeButtonText;

    // Created once and retained.
    private boolean mShowing;
    private android.hardware.biometrics.BiometricPrompt mBiometricPrompt;
    private CancellationSignal mCancellationSignal;
    private boolean mStartRespectingCancel;

    // Do not rely on the application's executor when calling into the framework's code.
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Executor mExecutor = mHandler::post;

    // Also created once and retained.
    private final android.hardware.biometrics.BiometricPrompt.AuthenticationCallback
            mAuthenticationCallback =
            new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(final int errorCode,
                        final CharSequence errString) {
                    mClientExecutor.execute(() -> {
                        CharSequence error = errString;
                        if (error == null) {
                            error = mContext.getString(R.string.default_error_msg) + " "
                                    + errorCode;
                        }
                        mClientAuthenticationCallback
                                .onAuthenticationError(Utils.isUnknownError(errorCode)
                                        ? BiometricPrompt.ERROR_VENDOR : errorCode, error);
                    });
                    cleanup();
                }

                @Override
                public void onAuthenticationHelp(final int helpCode,
                        final CharSequence helpString) {
                    // Don't forward the result to the client, since the dialog takes care of it.
                }

                @Override
                public void onAuthenticationSucceeded(
                        final android.hardware.biometrics.BiometricPrompt.AuthenticationResult
                                result) {
                    mClientExecutor.execute(
                            () -> mClientAuthenticationCallback.onAuthenticationSucceeded(
                                    new BiometricPrompt.AuthenticationResult(
                                            unwrapCryptoObject(result.getCryptoObject()))));
                    cleanup();
                }

                @Override
                public void onAuthenticationFailed() {
                    mClientExecutor.execute(mClientAuthenticationCallback::onAuthenticationFailed);
                }
            };

    // Also created once and retained.
    private final DialogInterface.OnClickListener mNegativeButtonListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mClientNegativeButtonListener.onClick(dialog, which);
                }
            };

    // Also created once and retained.
    @SuppressWarnings("deprecation")
    private final DialogInterface.OnClickListener mDeviceCredentialButtonListener =
            (dialog, which) -> {
                if (which == DialogInterface.BUTTON_NEGATIVE) {
                    final FragmentActivity activity = getActivity();
                    if (!(activity instanceof DeviceCredentialHandlerActivity)) {
                        Log.e(TAG, "Failed to check device credential. Parent handler not found.");
                        return;
                    }

                    final KeyguardManager km = activity.getSystemService(KeyguardManager.class);
                    if (km == null) {
                        Log.e(TAG, "Failed to check device credential. KeyguardManager was null.");
                        return;
                    }

                    // Pass along the title and subtitle from the biometric prompt.
                    final CharSequence title;
                    final CharSequence subtitle;
                    if (mBundle != null) {
                        title = mBundle.getCharSequence(BiometricPrompt.KEY_TITLE);
                        subtitle = mBundle.getCharSequence(BiometricPrompt.KEY_SUBTITLE);
                    } else {
                        title = null;
                        subtitle = null;
                    }

                    // Prevent the bridge from resetting until the confirmation activity finishes.
                    DeviceCredentialHandlerBridge bridge =
                            DeviceCredentialHandlerBridge.getInstanceIfNotNull();
                    if (bridge != null) {
                        bridge.startIgnoringReset();
                    }

                    // Launch a new instance of the confirm device credential Settings activity.
                    final Intent intent = km.createConfirmDeviceCredentialIntent(title, subtitle);
                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                    activity.startActivityForResult(intent, 0 /* requestCode */);
                }
            };

    /**
     * Creates a new instance of the {@link BiometricFragment}.
     */
    static BiometricFragment newInstance() {
        return new BiometricFragment();
    }

    /**
     * Sets the client's callback. This should be done whenever the lifecycle changes (orientation
     * changes).
     */
    void setCallbacks(Executor executor, DialogInterface.OnClickListener onClickListener,
            BiometricPrompt.AuthenticationCallback authenticationCallback) {
        mClientExecutor = executor;
        mClientNegativeButtonListener = onClickListener;
        mClientAuthenticationCallback = authenticationCallback;
    }

    /**
     * Sets the crypto object to be associated with the authentication. Should be called before
     * adding the fragment to guarantee that it's ready in onCreate().
     */
    void setCryptoObject(BiometricPrompt.CryptoObject crypto) {
        mCryptoObject = crypto;
    }

    /**
     * Cancel the authentication.
     */
    void cancel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDeviceCredentialAllowed()) {
            if (!mStartRespectingCancel) {
                Log.w(TAG, "Ignoring fast cancel signal");
                return;
            }
        }
        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
        }
        cleanup();
    }

    /**
     * Remove the fragment so that resources can be freed.
     */
    void cleanup() {
        mShowing = false;
        FragmentActivity activity = getActivity();
        if (getFragmentManager() != null) {
            getFragmentManager().beginTransaction().detach(this).commitAllowingStateLoss();
        }
        Utils.maybeFinishHandler(activity);
    }

    @Nullable
    protected CharSequence getNegativeButtonText() {
        return mNegativeButtonText;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    public void setBundle(Bundle bundle) {
        mBundle = bundle;
    }

    boolean isDeviceCredentialAllowed() {
        return mBundle.getBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Start the actual authentication when the fragment is attached.
        if (!mShowing) {
            mNegativeButtonText = mBundle.getCharSequence(BiometricPrompt.KEY_NEGATIVE_TEXT);

            final android.hardware.biometrics.BiometricPrompt.Builder builder =
                    new android.hardware.biometrics.BiometricPrompt.Builder(getContext());
            builder.setTitle(mBundle.getCharSequence(BiometricPrompt.KEY_TITLE))
                    .setSubtitle(mBundle.getCharSequence(BiometricPrompt.KEY_SUBTITLE))
                    .setDescription(mBundle.getCharSequence(BiometricPrompt.KEY_DESCRIPTION));

            final boolean allowDeviceCredential =
                    mBundle.getBoolean(BiometricPrompt.KEY_ALLOW_DEVICE_CREDENTIAL);

            // Provide our own negative button text if allowing device credential on <= P.
            if (allowDeviceCredential && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                mNegativeButtonText = getString(R.string.confirm_device_credential_password);
                builder.setNegativeButton(
                        mNegativeButtonText, mClientExecutor, mDeviceCredentialButtonListener);
            } else if (!TextUtils.isEmpty(mNegativeButtonText)) {
                builder.setNegativeButton(
                        mNegativeButtonText, mClientExecutor, mNegativeButtonListener);
            }

            // Set builder flags introduced in Q.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setConfirmationRequired(
                        mBundle.getBoolean((BiometricPrompt.KEY_REQUIRE_CONFIRMATION), true));
                builder.setDeviceCredentialAllowed(allowDeviceCredential);
            }

            if (allowDeviceCredential) {
                mStartRespectingCancel = false;
                mHandler.postDelayed(() -> {
                    // Hack almost over 9000, ignore cancel signal if it's within the first quarter
                    // second.
                    mStartRespectingCancel = true;
                }, 250 /* ms */);
            }

            mBiometricPrompt = builder.build();
            mCancellationSignal = new CancellationSignal();
            if (mCryptoObject == null) {
                mBiometricPrompt.authenticate(mCancellationSignal, mExecutor,
                        mAuthenticationCallback);
            } else {
                mBiometricPrompt.authenticate(wrapCryptoObject(mCryptoObject), mCancellationSignal,
                        mExecutor, mAuthenticationCallback);
            }
        }
        mShowing = true;
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private static BiometricPrompt.CryptoObject unwrapCryptoObject(
            android.hardware.biometrics.BiometricPrompt.CryptoObject cryptoObject) {
        if (cryptoObject == null) {
            return null;
        } else if (cryptoObject.getCipher() != null) {
            return new BiometricPrompt.CryptoObject(cryptoObject.getCipher());
        } else if (cryptoObject.getSignature() != null) {
            return new BiometricPrompt.CryptoObject(cryptoObject.getSignature());
        } else if (cryptoObject.getMac() != null) {
            return new BiometricPrompt.CryptoObject(cryptoObject.getMac());
        } else {
            return null;
        }
    }

    private static android.hardware.biometrics.BiometricPrompt.CryptoObject wrapCryptoObject(
            BiometricPrompt.CryptoObject cryptoObject) {
        if (cryptoObject == null) {
            return null;
        } else if (cryptoObject.getCipher() != null) {
            return new android.hardware.biometrics.BiometricPrompt.CryptoObject(
                    cryptoObject.getCipher());
        } else if (cryptoObject.getSignature() != null) {
            return new android.hardware.biometrics.BiometricPrompt.CryptoObject(
                    cryptoObject.getSignature());
        } else if (cryptoObject.getMac() != null) {
            return new android.hardware.biometrics.BiometricPrompt.CryptoObject(
                    cryptoObject.getMac());
        } else {
            return null;
        }
    }
}
