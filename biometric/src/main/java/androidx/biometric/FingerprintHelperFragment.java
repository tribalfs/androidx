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
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.CancellationSignal;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * A fragment that wraps the FingerprintManagerCompat and has the ability to continue authentication
 * across device configuration changes. This class is not meant to be preserved after process death;
 * for security reasons, the BiometricPromptCompat will automatically stop authentication when the
 * activity is no longer in the foreground.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressLint("SyntheticAccessor")
public class FingerprintHelperFragment extends Fragment {

    private static final String TAG = "FingerprintHelperFrag";

    static final int USER_CANCELED_FROM_NONE = 0;
    static final int USER_CANCELED_FROM_USER = 1;
    static final int USER_CANCELED_FROM_NEGATIVE_BUTTON = 2;

    // Re-set by the application, through BiometricPromptCompat upon orientation changes.
    @VisibleForTesting
    Executor mExecutor;
    @VisibleForTesting
    BiometricPrompt.AuthenticationCallback mClientAuthenticationCallback;

    // Re-set by BiometricPromptCompat upon orientation changes. This handler is used to send
    // messages from the AuthenticationCallbacks to the UI.
    private Handler mHandler;

    // Set once and retained.
    private boolean mShowing;
    private BiometricPrompt.CryptoObject mCryptoObject;

    // Created once and retained.
    private Context mContext;
    private int mCanceledFrom;
    private CancellationSignal mCancellationSignal;

    // Whether this fragment is launching the confirm device credential Settings activity.
    private boolean mConfirmingDeviceCredential;

    // Also created once and retained.
    @SuppressWarnings("deprecation")
    private final androidx.core.hardware.fingerprint.FingerprintManagerCompat.AuthenticationCallback
            mAuthenticationCallback =
            new androidx.core.hardware.fingerprint.FingerprintManagerCompat
                    .AuthenticationCallback() {

                private void dismissAndForwardResult(final int errMsgId,
                        final CharSequence errString) {
                    mHandler.obtainMessage(FingerprintDialogFragment.MSG_DISMISS_DIALOG_ERROR)
                            .sendToTarget();
                    if (!mConfirmingDeviceCredential) {
                        mExecutor.execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mClientAuthenticationCallback
                                                .onAuthenticationError(errMsgId, errString);
                                    }
                                });
                    }
                }

                @Override
                public void onAuthenticationError(final int errMsgId,
                        CharSequence errString) {
                    if (errMsgId == BiometricPrompt.ERROR_CANCELED) {
                        if (mCanceledFrom == USER_CANCELED_FROM_NONE) {
                            dismissAndForwardResult(errMsgId, errString);
                        }
                    } else if (errMsgId == BiometricPrompt.ERROR_LOCKOUT
                            || errMsgId == BiometricPrompt.ERROR_LOCKOUT_PERMANENT) {
                        dismissAndForwardResult(errMsgId, errString);
                    } else {
                        // Avoid passing a null error string to the client callback. This needs to
                        // be a final copy, since it's accessed in the runnable below.
                        final CharSequence errStringNonNull;
                        if (errString != null) {
                            errStringNonNull = errString;
                        } else {
                            Log.e(TAG, "Got null string for error message: " + errMsgId);
                            errStringNonNull =
                                    mContext.getResources().getString(R.string.default_error_msg);
                        }

                        // Ensure we are only sending publicly defined errors.
                        final int errMsgIdToSend = Utils.isUnknownError(errMsgId)
                                ? BiometricPrompt.ERROR_VENDOR : errMsgId;

                        mHandler.obtainMessage(FingerprintDialogFragment.MSG_SHOW_ERROR,
                                errMsgIdToSend, 0, errStringNonNull).sendToTarget();
                        if (!mConfirmingDeviceCredential) {
                            mHandler.postDelayed(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            mExecutor.execute(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mClientAuthenticationCallback
                                                            .onAuthenticationError(
                                                                    errMsgIdToSend,
                                                                    errStringNonNull);
                                                }
                                            });
                                        }
                                    },
                                    FingerprintDialogFragment.HIDE_DIALOG_DELAY);
                        }
                    }
                    cleanup();
                }

                @Override
                public void onAuthenticationHelp(final int helpMsgId,
                        final CharSequence helpString) {
                    mHandler.obtainMessage(FingerprintDialogFragment.MSG_SHOW_HELP, helpString)
                            .sendToTarget();
                    // Don't forward the result to the client, since the dialog takes care of it.
                }

                @Override
                public void onAuthenticationSucceeded(final androidx.core.hardware.fingerprint
                        .FingerprintManagerCompat.AuthenticationResult result) {
                    mHandler.obtainMessage(
                            FingerprintDialogFragment.MSG_DISMISS_DIALOG_AUTHENTICATED)
                            .sendToTarget();
                    mExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            mClientAuthenticationCallback.onAuthenticationSucceeded(
                                    new BiometricPrompt.AuthenticationResult(
                                            unwrapCryptoObject(result.getCryptoObject())));
                        }
                    });
                    cleanup();
                }

                @Override
                public void onAuthenticationFailed() {
                    mHandler.obtainMessage(FingerprintDialogFragment.MSG_SHOW_HELP,
                            mContext.getResources().getString(R.string.fingerprint_not_recognized))
                            .sendToTarget();
                    mExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            mClientAuthenticationCallback.onAuthenticationFailed();
                        }
                    });
                }
            };

    /**
     * Creates a new instance of the {@link FingerprintHelperFragment}.
     */
    static FingerprintHelperFragment newInstance() {
        return new FingerprintHelperFragment();
    }

    /**
     * Sets the crypto object to be associated with the authentication. Should be called before
     * adding the fragment to guarantee that it's ready in onCreate().
     */
    void setCryptoObject(BiometricPrompt.CryptoObject crypto) {
        mCryptoObject = crypto;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mContext = getContext();
    }

    @Override
    @SuppressWarnings("deprecation")
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        if (!mShowing) {
            mCancellationSignal = new CancellationSignal();
            mCanceledFrom = USER_CANCELED_FROM_NONE;
            androidx.core.hardware.fingerprint.FingerprintManagerCompat fingerprintManagerCompat =
                    androidx.core.hardware.fingerprint.FingerprintManagerCompat.from(mContext);
            if (handlePreAuthenticationErrors(fingerprintManagerCompat)) {
                mHandler.obtainMessage(
                        FingerprintDialogFragment.MSG_DISMISS_DIALOG_ERROR).sendToTarget();
                cleanup();
            } else {
                fingerprintManagerCompat.authenticate(
                        wrapCryptoObject(mCryptoObject),
                        0 /* flags */,
                        mCancellationSignal,
                        mAuthenticationCallback,
                        null /* handler */);
                mShowing = true;
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    /**
     * Sets the client's callback. This should be done whenever the lifecycle changes (orientation
     * changes).
     */
    void setCallback(Executor executor,
            BiometricPrompt.AuthenticationCallback callback) {
        mExecutor = executor;
        mClientAuthenticationCallback = callback;
    }

    /**
     * Pass a reference to the handler used by FingerprintDialogFragment to update the UI.
     */
    void setHandler(Handler handler) {
        mHandler = handler;
    }

    /**
     * Indicates whether this fragment has or is about to launch the confirm device credential
     * Settings activity and should therefore stop sending error signals back to the client.
     */
    void setConfirmingDeviceCredential(boolean confirmingDeviceCredential) {
        mConfirmingDeviceCredential = confirmingDeviceCredential;
    }

    /**
     * Cancel the authentication.
     *
     * @param canceledFrom one of the USER_CANCELED_FROM* constants
     */
    void cancel(int canceledFrom) {
        mCanceledFrom = canceledFrom;
        if (canceledFrom == USER_CANCELED_FROM_USER) {
            sendErrorToClient(BiometricPrompt.ERROR_USER_CANCELED);
        }

        if (mCancellationSignal != null) {
            mCancellationSignal.cancel();
        }
        cleanup();
    }

    /**
     * Remove the fragment so that resources can be freed.
     */
    private void cleanup() {
        mShowing = false;
        FragmentActivity activity = getActivity();
        if (getFragmentManager() != null) {
            getFragmentManager().beginTransaction().detach(this).commitAllowingStateLoss();
        }
        if (!mConfirmingDeviceCredential) {
            Utils.maybeFinishHandler(activity);
        }
    }

    /**
     * Check before starting authentication for basic conditions, notifies client and returns true
     * if conditions are not met
     */
    @SuppressWarnings("deprecation")
    private boolean handlePreAuthenticationErrors(
            androidx.core.hardware.fingerprint.FingerprintManagerCompat fingerprintManager) {
        if (!fingerprintManager.isHardwareDetected()) {
            sendErrorToClient(BiometricPrompt.ERROR_HW_UNAVAILABLE);
            return true;
        } else if (!fingerprintManager.hasEnrolledFingerprints()) {
            sendErrorToClient(BiometricPrompt.ERROR_NO_BIOMETRICS);
            return true;
        }
        return false;
    }

    /**
     * Bypasses the FingerprintManager authentication callback wrapper and sends it directly to the
     * client's callback, since the UI is not even showing yet.
     *
     * @param error The error code that will be sent to the client.
     */
    private void sendErrorToClient(final int error) {
        if (!mConfirmingDeviceCredential) {
            mClientAuthenticationCallback.onAuthenticationError(error,
                    getErrorString(mContext, error));
        }
    }

    /**
     * Only needs to provide a subset of the fingerprint error strings since the rest are translated
     * in FingerprintManager
     */
    private String getErrorString(Context context, int errorCode) {
        switch (errorCode) {
            case BiometricPrompt.ERROR_HW_NOT_PRESENT:
                return context.getString(R.string.fingerprint_error_hw_not_present);
            case BiometricPrompt.ERROR_HW_UNAVAILABLE:
                return context.getString(R.string.fingerprint_error_hw_not_available);
            case BiometricPrompt.ERROR_NO_BIOMETRICS:
                return context.getString(R.string.fingerprint_error_no_fingerprints);
            case BiometricPrompt.ERROR_USER_CANCELED:
                return context.getString(R.string.fingerprint_error_user_canceled);
            default:
                Log.e(TAG, "Unknown error code: " + errorCode);
                return context.getString(R.string.default_error_msg);
        }
    }

    @SuppressWarnings("deprecation")
    private static BiometricPrompt.CryptoObject unwrapCryptoObject(
            androidx.core.hardware.fingerprint.FingerprintManagerCompat.CryptoObject cryptoObject) {
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

    @SuppressWarnings("deprecation")
    private static androidx.core.hardware.fingerprint.FingerprintManagerCompat.CryptoObject
            wrapCryptoObject(BiometricPrompt.CryptoObject cryptoObject) {
        if (cryptoObject == null) {
            return null;
        } else if (cryptoObject.getCipher() != null) {
            return new androidx.core.hardware.fingerprint.FingerprintManagerCompat.CryptoObject(
                    cryptoObject.getCipher());
        } else if (cryptoObject.getSignature() != null) {
            return new androidx.core.hardware.fingerprint.FingerprintManagerCompat.CryptoObject(
                    cryptoObject.getSignature());
        } else if (cryptoObject.getMac() != null) {
            return new androidx.core.hardware.fingerprint.FingerprintManagerCompat.CryptoObject(
                    cryptoObject.getMac());
        } else {
            return null;
        }
    }
}
