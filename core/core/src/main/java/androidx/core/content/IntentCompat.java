/*
 * Copyright (C) 2011 The Android Open Source Project
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

package androidx.core.content;

import static android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS;

import static androidx.core.util.Preconditions.checkNotNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * Helper for accessing features in {@link android.content.Intent}.
 */
public final class IntentCompat {
    private IntentCompat() {
        /* Hide constructor */
    }

    /**
     * Activity Action: Creates a reminder.
     * <p>Input: {@link android.content.Intent#EXTRA_TITLE} The title of the
     * reminder that will be shown to the user.
     * {@link android.content.Intent#EXTRA_TEXT} The reminder text that will be
     * shown to the user. The intent should at least specify a title or a text.
     * {@link #EXTRA_TIME} The time when the reminder will
     * be shown to the user. The time is specified in milliseconds since the
     * Epoch (optional).
     * </p>
     * <p>Output: Nothing.</p>
     *
     * @see android.content.Intent#EXTRA_TITLE
     * @see android.content.Intent#EXTRA_TEXT
     * @see #EXTRA_TIME
     */
    @SuppressLint("ActionValue")
    public static final String ACTION_CREATE_REMINDER = "android.intent.action.CREATE_REMINDER";

    /**
     * Activity action: creates an intent to redirect the user to UI to turn on/off their
     * unused app restriction settings.
     */
    @SuppressLint("ActionValue")
    public static final String ACTION_UNUSED_APP_RESTRICTIONS =
            "android.intent.action.AUTO_REVOKE_PERMISSIONS";

    /**
     * A constant String that is associated with the Intent, used with
     * {@link android.content.Intent#ACTION_SEND} to supply an alternative to
     * {@link android.content.Intent#EXTRA_TEXT}
     * as HTML formatted text.  Note that you <em>must</em> also supply
     * {@link android.content.Intent#EXTRA_TEXT}.
     */
    public static final String EXTRA_HTML_TEXT = "android.intent.extra.HTML_TEXT";

    /**
     * Used as a boolean extra field in {@link android.content.Intent#ACTION_VIEW} intents to
     * indicate that content should immediately be played without any intermediate screens that
     * require additional user input, e.g. a profile selection screen or a details page.
     */
    public static final String EXTRA_START_PLAYBACK = "android.intent.extra.START_PLAYBACK";

    /**
     * Optional extra specifying a time in milliseconds since the Epoch. The value must be
     * non-negative.
     * <p>
     * Type: long
     * </p>
     */
    @SuppressLint("ActionValue")
    public static final String EXTRA_TIME = "android.intent.extra.TIME";

    /**
     * Indicates an activity optimized for Leanback mode, and that should
     * be displayed in the Leanback launcher.
     */
    public static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    /**
     * Make an Intent for the main activity of an application, without
     * specifying a specific activity to run but giving a selector to find
     * the activity.  This results in a final Intent that is structured
     * the same as when the application is launched from
     * Home.  For anything else that wants to launch an application in the
     * same way, it is important that they use an Intent structured the same
     * way, and can use this function to ensure this is the case.
     *
     * <p>The returned Intent has {@link Intent#ACTION_MAIN} as its action, and includes the
     * category {@link Intent#CATEGORY_LAUNCHER}.  This does <em>not</em> have
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} set, though typically you will want
     * to do that through {@link Intent#addFlags(int)} on the returned Intent.
     *
     * @param selectorAction The action name of the Intent's selector.
     * @param selectorCategory The name of a category to add to the Intent's
     * selector.
     * @return Returns a newly created Intent that can be used to launch the
     * activity as a main application entry.
     */
    @NonNull
    public static Intent makeMainSelectorActivity(@NonNull String selectorAction,
            @NonNull String selectorCategory) {
        if (Build.VERSION.SDK_INT >= 15) {
            return Intent.makeMainSelectorActivity(selectorAction, selectorCategory);
        } else {
            // Before api 15 you couldn't set a selector intent.
            // Fall back and just return an intent with the requested action/category,
            // even though it won't be a proper "main" intent.
            Intent intent = new Intent(selectorAction);
            intent.addCategory(selectorCategory);
            return intent;
        }
    }

    /**
     * Make an Intent to redirect the user to UI to manage their unused app restriction settings
     * for a particular app (e.g. permission revocation, app hibernation).
     *
     * Note: developers must first call {@link #areUnusedAppRestrictionsAvailable} to make sure
     * that unused app restriction features are available on the device before attempting to create
     * an intent using this method.
     *
     * Compatibility behavior:
     * <ul>
     * <li>SDK 31 and above, this method generates an intent with action {@code Intent
     * .ACTION_APPLICATION_DETAILS_SETTINGS} and {@code packageName} as data.
     * <li>SDK 30, this method generates an intent with action {@code Intent
     * .ACTION_AUTO_REVOKE_PERMISSIONS} and {@code packageName} as data.
     * <li>SDK 23 through 29, if {@link #areUnusedAppRestrictionsAvailable} returns true,
     * this method will generate an intent with action {@code Intent
     * .ACTION_AUTO_REVOKE_PERMISSIONS} and the package as the first system app that can
     * resolve the intent. Otherwise, this method will throw an
     * {@link UnsupportedOperationException}.
     * <li>SDK 22 and below, this method will throw an {@link UnsupportedOperationException}
     * </ul>
     *
     * @param context The {@link Context} of the calling application.
     * @param packageName The package name of the calling application.
     *
     * @return Returns a newly created Intent that can be used to launch an activity where users
     * can manage unused app restrictions for a specific app.
     */
    @NonNull
    public static Intent createManageUnusedAppRestrictionsIntent(@NonNull Context context,
            @NonNull String packageName) {
        if (!areUnusedAppRestrictionsAvailable(context)) {
            throw new UnsupportedOperationException(
                    "Unused App Restrictions are not available on this device");
        }

        // If the OS version is S+, generate the intent using the Application Details Settings
        // intent action to support compatibility with the App Hibernation feature
        // TODO: replace with VERSION_CODES.S once it's defined
        if (Build.VERSION.SDK_INT >= 31) {
            return new Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(Uri.parse(packageName));
        }

        Intent unusedAppRestrictionsIntent =
                new Intent(ACTION_UNUSED_APP_RESTRICTIONS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // If the OS version is R, then just add the package name to the intent.
        // No need to add any other data or flags, since we're relying on the Android R system
        // feature.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return unusedAppRestrictionsIntent.setData(Uri.parse(packageName));
        } else {
            PackageManager packageManager = context.getPackageManager();
            // Only allow apps with the Verifier role to resolve the intent.
            String verifierPackageName = getVerifierPackageName(packageManager,
                    packageManager.queryIntentActivities(
                            unusedAppRestrictionsIntent, /* flags= */ 0));
            // The package name shouldn't be null since we've already checked that there exists a
            // single Verifier on the device, but nonetheless we double-check here.
            unusedAppRestrictionsIntent.setPackage(checkNotNull(verifierPackageName));
            return unusedAppRestrictionsIntent;
        }
    }

    /**
     * Checks to see whether unused app restrictions (e.g. permission revocation, app hibernation)
     * are available on this device.
     *
     * Compatibility behavior:
     * <ul>
     * <li>SDK 30 and above, this method always returns {@code true} as unused app restrictions
     * are built into the Android OS.
     * <li>SDK 23 through 29, this method checks for a system app that can resolve the {@code
     * Intent.ACTION_AUTO_REVOKE_PERMISSIONS} intent.
     * <li>SDK 22 and below, this method always returns {@code false} as runtime permissions did
     * not exist yet.
     * </ul>
     *
     * @param context The {@link Context} of the calling application.
     *
     * @return Returns a boolean indicating whether permission auto-revocation is available on
     * the device, whether through the Android Operating System or a system app.
     */
    public static boolean areUnusedAppRestrictionsAvailable(@NonNull Context context) {
        // Return false if the Android OS version is before M, because Android M introduced runtime
        // permissions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

        // Check that the Android OS version is R+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return true;

        // Else, check for an app with the verifier role that can resolve the intent
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> intentResolvers =
                packageManager.queryIntentActivities(
                        new Intent(ACTION_UNUSED_APP_RESTRICTIONS), /* flags= */ 0);

        // Check that we were able to get the one Verifier's package name. If no Verifier or
        // more than one Verifier exists on the device, unused app restrictions are not available
        // on the device.
        return getVerifierPackageName(packageManager, intentResolvers) != null;
    }

    /**
     * Returns the package name of the one and only Verifier on the device. If none exist, this
     * will return {@code null}. Likewise, if multiple Verifiers exist, this method will throw a
     * {@link RuntimeException} as there can only ever be one verifier.
     */
    @Nullable
    private static String getVerifierPackageName(PackageManager packageManager,
            List<ResolveInfo> intentResolvers) {
        String verifierPackageName = null;

        for (ResolveInfo intentResolver: intentResolvers) {
            String packageName = intentResolver.activityInfo.packageName;
            if (packageManager.checkPermission("android.permission.PACKAGE_VERIFICATION_AGENT",
                    packageName) != PackageManager.PERMISSION_GRANTED) {
                continue;
            }

            if (verifierPackageName != null) {
                throw new RuntimeException("There can be only one required verifier");
            }
            verifierPackageName = packageName;
        }

        return verifierPackageName;
    }

    /**
     * Checks whether this application is exempt from unused app restrictions (e.g. permission
     * revocation, app hibernation).
     *
     * Compatibility behavior:
     * <ul>
     * <li>SDK 30 and above, this method returns the value of
     * {@link PackageManager#isAutoRevokeWhitelisted}
     * <li>SDK 23 through 29, this method returns {@code false}.
     * <li>SDK 22 and below, this method always returns {@code false} as runtime
     * permissions did not exist yet.
     * </ul>
     */
    public static boolean areUnusedAppRestrictionsAllowlisted(@NonNull Context context) {
        // Return false if the Android OS version is before M, because Android M introduced runtime
        // permissions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Api30Impl.areUnusedAppRestrictionsAllowlisted(context);
        }

        // TODO(b/177234481): Implement the backport behavior of this API
        return false;
    }

    /**
     * We create this static class to avoid Class Verification Failures from referencing a method
     * only added in Android R.
     *
     * <p>Gating references on SDK checks does not address class verification failures, hence the
     * need for this inner class.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private static class Api30Impl {
        private Api30Impl() {}
        static boolean areUnusedAppRestrictionsAllowlisted(@NonNull Context context) {
            return context.getPackageManager().isAutoRevokeWhitelisted();
        }
    }
}
