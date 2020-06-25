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

import static androidx.biometric.BiometricConstants.ERROR_NEGATIVE_BUTTON;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.biometric.BiometricManager.Authenticators;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A fragment that hosts the system-dependent UI for {@link BiometricPrompt} and coordinates logic
 * for the ongoing authentication session across device configuration changes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricFragment extends Fragment {
    private static final String TAG = "BiometricFragment";

    /**
     * Authentication was not canceled by the user but may have been canceled by the system.
     */
    static final int CANCELED_FROM_NONE = 0;

    /**
     * Authentication was canceled by the user (e.g. by pressing the system back button).
     */
    static final int CANCELED_FROM_USER = 1;

    /**
     * Authentication was canceled by the user by pressing the negative button on the prompt.
     */
    static final int CANCELED_FROM_NEGATIVE_BUTTON = 2;

    /**
     * Where authentication was canceled from.
     */
    @IntDef({CANCELED_FROM_NONE, CANCELED_FROM_USER, CANCELED_FROM_NEGATIVE_BUTTON})
    @Retention(RetentionPolicy.SOURCE)
    @interface CanceledFrom {}

    /**
     * Tag used to identify the {@link FingerprintDialogFragment} attached to the client
     * activity/fragment.
     */
    private static final String FINGERPRINT_DIALOG_FRAGMENT_TAG =
            "androidx.biometric.FingerprintDialogFragment";

    /**
     * The amount of time before the flag indicating whether to dismiss the fingerprint dialog
     * instantly can be changed.
     */
    private static final int DISMISS_INSTANTLY_DELAY_MS = 500;

    /**
     * The amount of time to wait before dismissing the fingerprint dialog after encountering an
     * error. Ignored if {@link DeviceUtils#shouldHideFingerprintDialog(Context, String)} is
     * {@code true}.
     */
    private static final int HIDE_DIALOG_DELAY_MS = 2000;

    /**
     * Request code used when launching the confirm device credential Settings activity.
     */
    private static final int REQUEST_CONFIRM_CREDENTIAL = 1;

    /**
     * Force the fingerprint dialog to appear for debugging. Must NOT be checked in as {@code true}.
     */
    private static final boolean DEBUG_FORCE_FINGERPRINT = false;

    /**
     * A handler used to post delayed events and to execute framework code.
     */
    @VisibleForTesting Handler mHandler = new Handler(Looper.getMainLooper());

    /**
     * An executor used by {@link android.hardware.biometrics.BiometricPrompt} to run framework
     * code.
     */
    private final Executor mPromptExecutor = new Executor() {
        @Override
        public void execute(@NonNull Runnable runnable) {
            mHandler.post(runnable);
        }
    };

    /**
     * The view model for the ongoing authentication session.
     */
    @VisibleForTesting BiometricViewModel mViewModel;

    /**
     * Creates a new instance of {@link BiometricFragment}.
     *
     * @return A {@link BiometricFragment}.
     */
    static BiometricFragment newInstance() {
        return new BiometricFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectViewModel();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) {
            cancelAuthentication(BiometricFragment.CANCELED_FROM_NONE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIRM_CREDENTIAL) {
            mViewModel.setConfirmingDeviceCredential(false);
            handleConfirmCredentialResult(resultCode);
        }
    }

    /**
     * Connects the {@link BiometricViewModel} for the ongoing authentication session to this
     * fragment.
     */
    private void connectViewModel() {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mViewModel = new ViewModelProvider(getActivity()).get(BiometricViewModel.class);

        mViewModel.getAuthenticationResult().observe(
                this,
                new Observer<BiometricPrompt.AuthenticationResult>() {
                    @Override
                    public void onChanged(
                            BiometricPrompt.AuthenticationResult authenticationResult) {
                        if (authenticationResult != null) {
                            onAuthenticationSucceeded(authenticationResult);
                            mViewModel.setAuthenticationResult(null);
                        }
                    }
                });

        mViewModel.getAuthenticationError().observe(
                this,
                new Observer<BiometricErrorData>() {
                    @Override
                    public void onChanged(BiometricErrorData authenticationError) {
                        if (authenticationError != null) {
                            onAuthenticationError(
                                    authenticationError.getErrorCode(),
                                    authenticationError.getErrorMessage());
                            mViewModel.setAuthenticationError(null);
                        }
                    }
                });

        mViewModel.getAuthenticationHelpMessage().observe(
                this,
                new Observer<CharSequence>() {
                    @Override
                    public void onChanged(CharSequence authenticationHelpMessage) {
                        if (authenticationHelpMessage != null) {
                            onAuthenticationHelp(authenticationHelpMessage);
                            mViewModel.setAuthenticationError(null);
                        }
                    }
                });

        mViewModel.isAuthenticationFailurePending().observe(
                this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean authenticationFailurePending) {
                        if (authenticationFailurePending) {
                            onAuthenticationFailed();
                            mViewModel.setAuthenticationFailurePending(false);
                        }
                    }
                });

        mViewModel.isNegativeButtonPressPending().observe(
                this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean negativeButtonPressPending) {
                        if (negativeButtonPressPending) {
                            if (isManagingDeviceCredentialButton()) {
                                onDeviceCredentialButtonPressed();
                            } else {
                                onCancelButtonPressed();
                            }
                            mViewModel.setNegativeButtonPressPending(false);
                        }
                    }
                });

        mViewModel.isFingerprintDialogCancelPending().observe(
                this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean fingerprintDialogCancelPending) {
                        if (fingerprintDialogCancelPending) {
                            cancelAuthentication(BiometricFragment.CANCELED_FROM_USER);
                            dismiss();
                            mViewModel.setFingerprintDialogCancelPending(false);
                        }
                    }
                });
    }

    /**
     * Shows the prompt UI to the user and begins an authentication session.
     *
     * @param info An object describing the appearance and behavior of the prompt.
     * @param crypto A crypto object to be associated with this authentication.
     */
    void authenticate(
            @NonNull BiometricPrompt.PromptInfo info,
            @Nullable BiometricPrompt.CryptoObject crypto) {

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not launching prompt. Client activity was null.");
            return;
        }

        mViewModel.setPromptInfo(info);
        mViewModel.setCryptoObject(crypto);
        if (isManagingDeviceCredentialButton()) {
            mViewModel.setNegativeButtonText(
                    getString(R.string.confirm_device_credential_password));
        } else {
            // Don't override the negative button text from the client.
            mViewModel.setNegativeButtonText(null);
        }

        // Fall back to device credential immediately if no biometrics are enrolled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && isManagingDeviceCredentialButton()
                && BiometricManager.from(activity).canAuthenticate(Authenticators.BIOMETRIC_WEAK)
                        != BiometricManager.BIOMETRIC_SUCCESS) {
            mViewModel.setAwaitingResult(true);
            launchConfirmCredentialActivity();
            return;
        }

        if (!mViewModel.isPromptShowing()) {
            if (getContext() == null) {
                Log.w(TAG, "Not showing biometric prompt. Context is null.");
                return;
            }

            mViewModel.setPromptShowing(true);
            mViewModel.setAwaitingResult(true);
            if (isUsingFingerprintDialog()) {
                showFingerprintDialogForAuthentication();
            } else {
                showBiometricPromptForAuthentication();
            }
        }
    }

    /**
     * Shows the fingerprint dialog UI to the user and begins authentication.
     */
    @SuppressWarnings("deprecation")
    private void showFingerprintDialogForAuthentication() {
        final Context context = requireContext();
        androidx.core.hardware.fingerprint.FingerprintManagerCompat fingerprintManagerCompat =
                androidx.core.hardware.fingerprint.FingerprintManagerCompat.from(context);
        final int errorCode = checkForFingerprintPreAuthenticationErrors(fingerprintManagerCompat);
        if (errorCode != 0) {
            sendErrorAndDismiss(
                    errorCode, ErrorUtils.getFingerprintErrorString(getContext(), errorCode));
            return;
        }

        if (isAdded()) {
            final boolean shouldHideFingerprintDialog =
                    DeviceUtils.shouldHideFingerprintDialog(context, Build.MODEL);
            if (mViewModel.isFingerprintDialogDismissedInstantly() != shouldHideFingerprintDialog) {
                mHandler.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                mViewModel.setFingerprintDialogDismissedInstantly(
                                        shouldHideFingerprintDialog);
                            }
                        },
                        DISMISS_INSTANTLY_DELAY_MS);
            }

            if (!shouldHideFingerprintDialog) {
                final FingerprintDialogFragment dialog = FingerprintDialogFragment.newInstance();
                dialog.show(getParentFragmentManager(), FINGERPRINT_DIALOG_FRAGMENT_TAG);
            }

            mViewModel.setCanceledFrom(CANCELED_FROM_NONE);
            fingerprintManagerCompat.authenticate(
                    CryptoObjectUtils.wrapForFingerprintManager(mViewModel.getCryptoObject()),
                    0 /* flags */,
                    mViewModel.getCancellationSignalProvider().getFingerprintCancellationSignal(),
                    mViewModel.getAuthenticationCallbackProvider().getFingerprintCallback(),
                    null /* handler */);
        }
    }

    /**
     * Shows the framework {@link android.hardware.biometrics.BiometricPrompt} UI to the user and
     * begins authentication.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private void showBiometricPromptForAuthentication() {
        final Context context = requireContext();
        final android.hardware.biometrics.BiometricPrompt.Builder builder =
                Api28Impl.createPromptBuilder(context);

        final CharSequence title = mViewModel.getTitle();
        final CharSequence subtitle = mViewModel.getSubtitle();
        final CharSequence description = mViewModel.getDescription();
        if (title != null) {
            Api28Impl.setTitle(builder, title);
        }
        if (subtitle != null) {
            Api28Impl.setSubtitle(builder, subtitle);
        }
        if (description != null) {
            Api28Impl.setDescription(builder, description);
        }

        final CharSequence negativeButtonText = mViewModel.getNegativeButtonText();
        if (!TextUtils.isEmpty(negativeButtonText)) {
            Api28Impl.setNegativeButton(
                    builder,
                    negativeButtonText,
                    mViewModel.getClientExecutor(),
                    mViewModel.getNegativeButtonListener());
        }

        // Set builder flags introduced in Q.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29Impl.setConfirmationRequired(builder, mViewModel.isConfirmationRequired());
            Api29Impl.setDeviceCredentialAllowed(builder, mViewModel.isDeviceCredentialAllowed());
        }

        final android.hardware.biometrics.BiometricPrompt biometricPrompt =
                Api28Impl.buildPrompt(builder);
        final android.os.CancellationSignal cancellationSignal =
                mViewModel.getCancellationSignalProvider().getBiometricCancellationSignal();
        final android.hardware.biometrics.BiometricPrompt.AuthenticationCallback callback =
                mViewModel.getAuthenticationCallbackProvider().getBiometricCallback();
        final BiometricPrompt.CryptoObject cryptoObject = mViewModel.getCryptoObject();
        if (mViewModel.getCryptoObject() == null) {
            Api28Impl.authenticate(biometricPrompt, cancellationSignal, mPromptExecutor, callback);
        } else {
            android.hardware.biometrics.BiometricPrompt.CryptoObject wrappedCryptoObject =
                    Objects.requireNonNull(
                            CryptoObjectUtils.wrapForBiometricPrompt(cryptoObject));
            Api28Impl.authenticate(
                    biometricPrompt,
                    wrappedCryptoObject,
                    cancellationSignal,
                    mPromptExecutor,
                    callback);
        }
    }

    /**
     * Cancels the ongoing authentication session and sends an error to the client callback.
     *
     * @param canceledFrom Where authentication was canceled from.
     */
    void cancelAuthentication(@CanceledFrom int canceledFrom) {
        if (isUsingFingerprintDialog()) {
            mViewModel.setCanceledFrom(canceledFrom);
            if (canceledFrom == CANCELED_FROM_USER) {
                final int errorCode = BiometricPrompt.ERROR_USER_CANCELED;
                sendErrorToClient(
                        errorCode, ErrorUtils.getFingerprintErrorString(getContext(), errorCode));
            }
        }
        mViewModel.getCancellationSignalProvider().cancel();
    }

    /**
     * Removes this fragment and any associated UI from the client activity/fragment.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dismiss() {
        mViewModel.setPromptShowing(false);
        dismissFingerprintDialog();
        if (!mViewModel.isConfirmingDeviceCredential() && isAdded()) {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    /**
     * Removes the fingerprint dialog UI from the client activity/fragment.
     */
    private void dismissFingerprintDialog() {
        mViewModel.setPromptShowing(false);
        if (isAdded()) {
            final FragmentManager fragmentManager = getParentFragmentManager();
            final FingerprintDialogFragment fingerprintDialog =
                    (FingerprintDialogFragment) fragmentManager.findFragmentByTag(
                            FINGERPRINT_DIALOG_FRAGMENT_TAG);
            if (fingerprintDialog != null) {
                if (fingerprintDialog.isAdded()) {
                    fingerprintDialog.dismissAllowingStateLoss();
                } else {
                    fragmentManager.beginTransaction().remove(fingerprintDialog)
                            .commitAllowingStateLoss();
                }
            }
        }
    }

    /**
     * Callback that is run when the view model receives a successful authentication result.
     *
     * @param result An object containing authentication-related data.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    @VisibleForTesting
    void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
        sendSuccessAndDismiss(result);
    }

    /**
     * Callback that is run when the view model receives an unrecoverable error result.
     *
     * @param errorCode An integer ID associated with the error.
     * @param errorMessage A human-readable string that describes the error.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    @VisibleForTesting
    void onAuthenticationError(final int errorCode, @Nullable CharSequence errorMessage) {
        // Ensure we're only sending publicly defined errors.
        final int knownErrorCode = ErrorUtils.isKnownError(errorCode)
                ? errorCode
                : BiometricPrompt.ERROR_VENDOR;

        if (isUsingFingerprintDialog()) {
            // Avoid passing a null error string to the client callback.
            final CharSequence errorString = errorMessage != null
                    ? errorMessage
                    : ErrorUtils.getFingerprintErrorString(getContext(), errorCode);

            if (errorCode == BiometricPrompt.ERROR_CANCELED) {
                // User-initiated cancellation errors should already be handled.
                if (mViewModel.getCanceledFrom() == CANCELED_FROM_NONE) {
                    sendErrorToClient(errorCode, errorString);
                }
                dismiss();
            } else {
                if (mViewModel.isFingerprintDialogDismissedInstantly()) {
                    sendErrorAndDismiss(knownErrorCode, errorString);
                } else {
                    showFingerprintErrorMessage(errorString);
                    mHandler.postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    sendErrorAndDismiss(errorCode, errorString);
                                }
                            },
                            getDismissDialogDelay());
                }

                // Always set this to true. In case the user tries to authenticate again
                // the UI will not be shown.
                mViewModel.setFingerprintDialogDismissedInstantly(true);
            }
        } else {
            final CharSequence errorString = errorMessage != null
                    ? errorMessage
                    : getString(R.string.default_error_msg)
                            + " "
                            + errorCode;
            sendErrorAndDismiss(knownErrorCode, errorString);
        }
    }

    /**
     * Callback that is run when the view model receives a recoverable error or help message.
     *
     * @param helpMessage A human-readable error/help message.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void onAuthenticationHelp(@NonNull CharSequence helpMessage) {
        if (isUsingFingerprintDialog()) {
            showFingerprintErrorMessage(helpMessage);
        }
    }

    /**
     * Callback that is run when the view model reports a failed authentication attempt.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void onAuthenticationFailed() {
        if (isUsingFingerprintDialog()) {
            showFingerprintErrorMessage(getString(R.string.fingerprint_not_recognized));
        }
        sendFailureToClient();
    }

    /**
     * Callback that is run when the view model reports that the device credential fallback
     * button has been pressed on the prompt.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void onDeviceCredentialButtonPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "Failed to check device credential. Not supported prior to API 21.");
            return;
        }
        launchConfirmCredentialActivity();
    }

    /**
     * Callback that is run when the view model reports that the cancel button has been pressed on
     * the prompt.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void onCancelButtonPressed() {
        final BiometricPrompt.PromptInfo info = mViewModel.getPromptInfo();
        final CharSequence errorText = info != null
                ? info.getNegativeButtonText()
                : null;
        sendErrorAndDismiss(
                ERROR_NEGATIVE_BUTTON,
                errorText != null
                        ? errorText
                        : getString(R.string.default_error_msg));
        cancelAuthentication(BiometricFragment.CANCELED_FROM_NEGATIVE_BUTTON);
    }

    /**
     * Launches the confirm device credential Settings activity, where the user can authenticate
     * using their PIN, pattern, or password.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private void launchConfirmCredentialActivity() {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Failed to check device credential. Client FragmentActivity not found.");
            return;
        }

        // Get the KeyguardManager service in whichever way the platform supports.
        final KeyguardManager keyguardManager = KeyguardUtils.getKeyguardManager(activity);
        if (keyguardManager == null) {
            Log.e(TAG, "Failed to check device credential. KeyguardManager not found.");
            handleConfirmCredentialResult(Activity.RESULT_CANCELED);
            return;
        }

        // Pass along the title and subtitle/description from the biometric prompt.
        final CharSequence title = mViewModel.getTitle();
        final CharSequence subtitle = mViewModel.getSubtitle();
        final CharSequence description = mViewModel.getDescription();
        final CharSequence credentialDescription = subtitle != null ? subtitle : description;

        final Intent intent = Api21Impl.createConfirmDeviceCredentialIntent(
                keyguardManager, title, credentialDescription);

        if (intent == null) {
            Log.e(TAG, "Failed to check device credential. Got null intent from Keyguard.");
            handleConfirmCredentialResult(Activity.RESULT_CANCELED);
            return;
        }

        mViewModel.setConfirmingDeviceCredential(true);

        // Dismiss the fingerprint dialog before launching the activity.
        if (isUsingFingerprintDialog()) {
            dismissFingerprintDialog();
        }

        // Launch a new instance of the confirm device credential Settings activity.
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        startActivityForResult(intent, REQUEST_CONFIRM_CREDENTIAL);
    }

    /**
     * Processes the result returned by the confirm device credential Settings activity.
     *
     * @param resultCode The result code from the Settings activity.
     */
    private void handleConfirmCredentialResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            // Device credential auth succeeded. This is incompatible with crypto.
            sendSuccessAndDismiss(
                    new BiometricPrompt.AuthenticationResult(null /* crypto */));
        } else {
            // Device credential auth failed. Assume this is due to the user canceling.
            sendErrorAndDismiss(
                    BiometricConstants.ERROR_USER_CANCELED,
                    getString(R.string.generic_error_user_canceled));
        }
    }

    /**
     * Updates the fingerprint dialog to show an error message to the user.
     *
     * @param errorMessage The error message to show on the dialog.
     */
    private void showFingerprintErrorMessage(@Nullable CharSequence errorMessage) {
        final CharSequence helpMessage = errorMessage != null
                ? errorMessage
                : getString(R.string.default_error_msg);
        mViewModel.setFingerprintDialogState(FingerprintDialogFragment.STATE_FINGERPRINT_ERROR);
        mViewModel.setFingerprintDialogHelpMessage(helpMessage);
    }

    /**
     * Sends a successful authentication result to the client and dismisses the prompt.
     *
     * @param result An object containing authentication-related data.
     *
     * @see #sendSuccessToClient(BiometricPrompt.AuthenticationResult)
     */
    private void sendSuccessAndDismiss(@NonNull BiometricPrompt.AuthenticationResult result) {
        sendSuccessToClient(result);
        dismiss();
    }

    /**
     * Sends an unrecoverable error result to the client and dismisses the prompt.
     *
     * @param errorCode An integer ID associated with the error.
     * @param errorString A human-readable string that describes the error.
     *
     * @see #sendErrorToClient(int, CharSequence)
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void sendErrorAndDismiss(int errorCode, @NonNull CharSequence errorString) {
        sendErrorToClient(errorCode, errorString);
        dismiss();
    }


    /**
     * Sends a successful authentication result to the client callback.
     *
     * @param result An object containing authentication-related data.
     *
     * @see #sendSuccessAndDismiss(BiometricPrompt.AuthenticationResult)
     * @see BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded(
     *  BiometricPrompt.AuthenticationResult)
     */
    private void sendSuccessToClient(@NonNull final BiometricPrompt.AuthenticationResult result) {
        if (!mViewModel.isAwaitingResult()) {
            Log.w(TAG, "Success not sent to client. Client is not awaiting a result.");
            return;
        }

        mViewModel.setAwaitingResult(false);
        mViewModel.getClientExecutor().execute(
                new Runnable() {
                    @Override
                    public void run() {
                        mViewModel.getClientCallback().onAuthenticationSucceeded(result);
                    }
                });
    }

    /**
     * Sends an unrecoverable error result to the client callback.
     *
     * @param errorCode An integer ID associated with the error.
     * @param errorString A human-readable string that describes the error.
     *
     * @see #sendErrorAndDismiss(int, CharSequence)
     * @see BiometricPrompt.AuthenticationCallback#onAuthenticationError(int, CharSequence)
     */
    private void sendErrorToClient(final int errorCode, @NonNull final CharSequence errorString) {
        if (mViewModel.isConfirmingDeviceCredential()) {
            Log.v(TAG, "Error not sent to client. User is confirming their device credential.");
            return;
        }

        if (!mViewModel.isAwaitingResult()) {
            Log.w(TAG, "Error not sent to client. Client is not awaiting a result.");
            return;
        }

        mViewModel.setAwaitingResult(false);
        mViewModel.getClientExecutor().execute(new Runnable() {
            @Override
            public void run() {
                mViewModel.getClientCallback().onAuthenticationError(errorCode, errorString);
            }
        });
    }

    /**
     * Sends an authentication failure event to the client callback.
     *
     * @see BiometricPrompt.AuthenticationCallback#onAuthenticationFailed()
     */
    private void sendFailureToClient() {
        if (!mViewModel.isAwaitingResult()) {
            Log.w(TAG, "Failure not sent to client. Client is not awaiting a result.");
            return;
        }

        mViewModel.getClientExecutor().execute(new Runnable() {
            @Override
            public void run() {
                mViewModel.getClientCallback().onAuthenticationFailed();
            }
        });
    }

    /**
     * Checks for possible error conditions prior to starting fingerprint authentication.
     *
     * @return 0 if there is no error, or a nonzero integer identifying the specific error.
     */
    @SuppressWarnings("deprecation")
    private static int checkForFingerprintPreAuthenticationErrors(
            androidx.core.hardware.fingerprint.FingerprintManagerCompat fingerprintManager) {
        if (!fingerprintManager.isHardwareDetected()) {
            return BiometricPrompt.ERROR_HW_NOT_PRESENT;
        } else if (!fingerprintManager.hasEnrolledFingerprints()) {
            return BiometricPrompt.ERROR_NO_BIOMETRICS;
        }
        return 0;
    }

    /**
     * Checks if this fragment is responsible for drawing and handling the result of a device
     * credential fallback button on the prompt.
     *
     * @return Whether this fragment is managing a device credential button for the prompt.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean isManagingDeviceCredentialButton() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && mViewModel.isDeviceCredentialAllowed();
    }

    /**
     * Checks if this fragment should display the fingerprint dialog authentication UI to the user,
     * rather than delegate to the framework {@link android.hardware.biometrics.BiometricPrompt}.
     *
     * @return Whether this fragment should display the fingerprint dialog UI.
     */
    private boolean isUsingFingerprintDialog() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P || shouldForceFingerprint();
    }

    /**
     * Checks if this fragment should always display the fingerprint dialog authentication UI,
     * regardless of Android version.
     *
     * <p>This is needed to force some devices to fall back to fingerprint in order to support
     * strong (crypto-based) authentication.
     *
     * @see DeviceUtils#shouldUseFingerprintForCrypto(Context, String, String)
     */
    private boolean shouldForceFingerprint() {
        final FragmentActivity activity = getActivity();
        return DEBUG_FORCE_FINGERPRINT || (activity != null && mViewModel.getCryptoObject() != null
                && DeviceUtils.shouldUseFingerprintForCrypto(
                        activity, Build.MANUFACTURER, Build.MODEL));
    }

    /**
     * Checks if the client activity is currently changing configurations (e.g. rotating screen
     * orientation).
     *
     * @return Whether the client activity is changing configurations.
     */
    private boolean isChangingConfigurations() {
        final FragmentActivity activity = getActivity();
        return activity != null && activity.isChangingConfigurations();
    }

    /**
     * Gets the amount of time to wait after receiving an unrecoverable error before dismissing the
     * fingerprint dialog and forwarding the error to the client.
     *
     * <p>This method respects the result of
     * {@link DeviceUtils#shouldHideFingerprintDialog(Context, String)} and returns 0 if the latter
     * is {@code true}.
     *
     * @return The delay (in milliseconds) to apply before hiding the fingerprint dialog.
     */
    private int getDismissDialogDelay() {
        Context context = getContext();
        return context != null && DeviceUtils.shouldHideFingerprintDialog(context, Build.MODEL)
                ? 0
                : HIDE_DIALOG_DELAY_MS;
    }

    /**
     * Nested class to avoid verification errors for methods introduced in Android 10 (API 29).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private static class Api29Impl {
        /**
         * Sets the "confirmation required" option for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param confirmationRequired The value for the "confirmation required" option.
         */
        static void setConfirmationRequired(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                boolean confirmationRequired) {
            builder.setConfirmationRequired(confirmationRequired);
        }

        /**
         * Sets the "device credential allowed" option for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param deviceCredentialAllowed The value for the "device credential allowed" option.
         */
        @SuppressWarnings("deprecation")
        static void setDeviceCredentialAllowed(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                boolean deviceCredentialAllowed) {
            builder.setDeviceCredentialAllowed(deviceCredentialAllowed);
        }
    }

    /**
     * Nested class to avoid verification errors for methods introduced in Android 9.0 (API 28).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private static class Api28Impl {
        /**
         * Creates an instance of the framework class
         * {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         *
         * @param context The application or activity context.
         * @return An instance of {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         */
        @NonNull
        static android.hardware.biometrics.BiometricPrompt.Builder createPromptBuilder(
                @NonNull Context context) {
            return new android.hardware.biometrics.BiometricPrompt.Builder(context);
        }

        /**
         * Sets the title for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param title The title for the prompt.
         */
        static void setTitle(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                @NonNull CharSequence title) {
            builder.setTitle(title);
        }

        /**
         * Sets the subtitle for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param subtitle The subtitle for the prompt.
         */
        static void setSubtitle(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                @NonNull CharSequence subtitle) {
            builder.setSubtitle(subtitle);
        }

        /**
         * Sets the description for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param description The description for the prompt.
         */
        static void setDescription(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                @NonNull CharSequence description) {
            builder.setDescription(description);
        }

        /**
         * Sets the negative button text and behavior for the given framework prompt builder.
         *
         * @param builder An instance of
         *                {@link android.hardware.biometrics.BiometricPrompt.Builder}.
         * @param text The text for the negative button.
         * @param executor An executor for the negative button callback.
         * @param listener A listener for the negative button press event.
         */
        static void setNegativeButton(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder,
                @NonNull CharSequence text,
                @NonNull Executor executor,
                @NonNull DialogInterface.OnClickListener listener) {
            builder.setNegativeButton(text, executor, listener);
        }

        /**
         * Creates an instance of the framework class
         * {@link android.hardware.biometrics.BiometricPrompt} from the given builder.
         *
         * @param builder The builder for the prompt.
         * @return An instance of {@link android.hardware.biometrics.BiometricPrompt}.
         */
        @NonNull
        static android.hardware.biometrics.BiometricPrompt buildPrompt(
                @NonNull android.hardware.biometrics.BiometricPrompt.Builder builder) {
            return builder.build();
        }

        /**
         * Starts (non-crypto) authentication for the given framework biometric prompt.
         *
         * @param biometricPrompt An instance of
         *                        {@link android.hardware.biometrics.BiometricPrompt}.
         * @param cancellationSignal A cancellation signal object for the prompt.
         * @param executor An executor for authentication callbacks.
         * @param callback An object that will receive authentication events.
         */
        static void authenticate(
                @NonNull android.hardware.biometrics.BiometricPrompt biometricPrompt,
                @NonNull android.os.CancellationSignal cancellationSignal,
                @NonNull Executor executor,
                @NonNull android.hardware.biometrics.BiometricPrompt.AuthenticationCallback
                        callback) {
            biometricPrompt.authenticate(cancellationSignal, executor, callback);
        }

        /**
         * Starts (crypto-based) authentication for the given framework biometric prompt.
         *
         * @param biometricPrompt An instance of
         *                        {@link android.hardware.biometrics.BiometricPrompt}.
         * @param crypto A crypto object associated with the given authentication.
         * @param cancellationSignal A cancellation signal object for the prompt.
         * @param executor An executor for authentication callbacks.
         * @param callback An object that will receive authentication events.
         */
        static void authenticate(
                @NonNull android.hardware.biometrics.BiometricPrompt biometricPrompt,
                @NonNull android.hardware.biometrics.BiometricPrompt.CryptoObject crypto,
                @NonNull android.os.CancellationSignal cancellationSignal,
                @NonNull Executor executor,
                @NonNull android.hardware.biometrics.BiometricPrompt.AuthenticationCallback
                        callback) {
            biometricPrompt.authenticate(crypto, cancellationSignal, executor, callback);
        }
    }

    /**
     * Nested class to avoid verification errors for methods introduced in Android 5.0 (API 21).
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static class Api21Impl {
        /**
         * Calls
         * {@link KeyguardManager#createConfirmDeviceCredentialIntent(CharSequence, CharSequence)}
         * for the given keyguard manager.
         *
         * @param keyguardManager An instance of {@link KeyguardManager}.
         * @param title The title for the confirm device credential activity.
         * @param description The description for the confirm device credential activity.
         * @return An intent that can be used to launch the confirm device credential activity.
         */
        @SuppressWarnings("deprecation")
        @Nullable
        static Intent createConfirmDeviceCredentialIntent(
                @NonNull KeyguardManager keyguardManager,
                @Nullable CharSequence title,
                @Nullable CharSequence description) {
            return keyguardManager.createConfirmDeviceCredentialIntent(title, description);
        }
    }
}
