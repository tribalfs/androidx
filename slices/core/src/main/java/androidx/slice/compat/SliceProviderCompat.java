/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.slice.compat;

import static android.app.slice.Slice.HINT_SHORTCUT;
import static android.app.slice.Slice.HINT_TITLE;
import static android.app.slice.SliceProvider.SLICE_TYPE;

import static androidx.slice.core.SliceHints.HINT_PERMISSION_REQUEST;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.util.Log;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.collection.ArraySet;
import androidx.core.util.Preconditions;
import androidx.slice.Slice;
import androidx.slice.SliceProvider;
import androidx.slice.SliceSpec;
import androidx.slice.core.R;
import androidx.slice.core.SliceHints;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @hide
 */
@RestrictTo(Scope.LIBRARY)
public class SliceProviderCompat extends ContentProvider {

    private static final String TAG = "SliceProvider";

    public static final String EXTRA_BIND_URI = "slice_uri";
    public static final String METHOD_SLICE = "bind_slice";
    public static final String METHOD_MAP_INTENT = "map_slice";
    public static final String METHOD_PIN = "pin_slice";
    public static final String METHOD_UNPIN = "unpin_slice";
    public static final String METHOD_GET_PINNED_SPECS = "get_specs";
    public static final String METHOD_MAP_ONLY_INTENT = "map_only";

    public static final String EXTRA_INTENT = "slice_intent";
    public static final String EXTRA_SLICE = "slice";
    public static final String EXTRA_SUPPORTED_SPECS = "specs";
    public static final String EXTRA_SUPPORTED_SPECS_REVS = "revs";
    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_PROVIDER_PKG = "provider_pkg";
    private static final String DATA_PREFIX = "slice_data_";

    private static final long SLICE_BIND_ANR = 2000;

    private static final boolean DEBUG = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SliceProvider mSliceProvider;
    private CompatPinnedList mPinnedList;

    private String mCallback;

    public SliceProviderCompat(SliceProvider provider) {
        mSliceProvider = provider;
    }

    @Override
    public boolean onCreate() {
        mPinnedList = new CompatPinnedList(getContext(),
                DATA_PREFIX + mSliceProvider.getClass().getName());
        return mSliceProvider.onCreateSliceProvider();
    }

    @Override
    public final int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "update " + uri);
        return 0;
    }

    @Override
    public final int delete(Uri uri, String selection, String[] selectionArgs) {
        if (DEBUG) Log.d(TAG, "delete " + uri);
        return 0;
    }

    @Override
    public final Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (DEBUG) Log.d(TAG, "query " + uri);
        return null;
    }

    @Override
    public final Cursor query(Uri uri, String[] projection, String selection, String[]
            selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        if (DEBUG) Log.d(TAG, "query " + uri);
        return null;
    }

    @Override
    public final Cursor query(Uri uri, String[] projection, Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        if (DEBUG) Log.d(TAG, "query " + uri);
        return null;
    }

    @Override
    public final Uri insert(Uri uri, ContentValues values) {
        if (DEBUG) Log.d(TAG, "insert " + uri);
        return null;
    }

    @Override
    public final String getType(Uri uri) {
        if (DEBUG) Log.d(TAG, "getFormat " + uri);
        return SLICE_TYPE;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (method.equals(METHOD_SLICE)) {
            Uri uri = extras.getParcelable(EXTRA_BIND_URI);
            if (Binder.getCallingUid() != Process.myUid()) {
                getContext().enforceUriPermission(uri, Binder.getCallingPid(),
                        Binder.getCallingUid(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        "Slice binding requires the permission BIND_SLICE");
            }
            Set<SliceSpec> specs = getSpecs(extras);

            Slice s = handleBindSlice(uri, specs, getCallingPackage());
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_SLICE, s.toBundle());
            return b;
        } else if (method.equals(METHOD_MAP_INTENT)) {
            Intent intent = extras.getParcelable(EXTRA_INTENT);
            Uri uri = mSliceProvider.onMapIntentToUri(intent);
            Bundle b = new Bundle();
            if (uri != null) {
                Set<SliceSpec> specs = getSpecs(extras);
                Slice s = handleBindSlice(uri, specs, getCallingPackage());
                b.putParcelable(EXTRA_SLICE, s.toBundle());
            } else {
                b.putParcelable(EXTRA_SLICE, null);
            }
            return b;
        } else if (method.equals(METHOD_MAP_ONLY_INTENT)) {
            Intent intent = extras.getParcelable(EXTRA_INTENT);
            Uri uri = mSliceProvider.onMapIntentToUri(intent);
            Bundle b = new Bundle();
            b.putParcelable(EXTRA_SLICE, uri);
            return b;
        } else if (method.equals(METHOD_PIN)) {
            Uri uri = extras.getParcelable(EXTRA_BIND_URI);
            Set<SliceSpec> specs = getSpecs(extras);
            String pkg = extras.getString(EXTRA_PKG);
            if (mPinnedList.addPin(uri, pkg, specs)) {
                handleSlicePinned(uri);
            }
            return null;
        } else if (method.equals(METHOD_UNPIN)) {
            Uri uri = extras.getParcelable(EXTRA_BIND_URI);
            String pkg = extras.getString(EXTRA_PKG);
            if (mPinnedList.removePin(uri, pkg)) {
                handleSliceUnpinned(uri);
            }
            return null;
        } else if (method.equals(METHOD_GET_PINNED_SPECS)) {
            Uri uri = extras.getParcelable(EXTRA_BIND_URI);
            Bundle b = new Bundle();
            addSpecs(b, mPinnedList.getSpecs(uri));
            return b;
        }
        return super.call(method, arg, extras);
    }

    private void handleSlicePinned(final Uri sliceUri) {
        mCallback = "onSlicePinned";
        mHandler.postDelayed(mAnr, SLICE_BIND_ANR);
        try {
            mSliceProvider.onSlicePinned(sliceUri);
        } finally {
            mHandler.removeCallbacks(mAnr);
        }
    }

    private void handleSliceUnpinned(final Uri sliceUri) {
        mCallback = "onSliceUnpinned";
        mHandler.postDelayed(mAnr, SLICE_BIND_ANR);
        try {
            mSliceProvider.onSliceUnpinned(sliceUri);
        } finally {
            mHandler.removeCallbacks(mAnr);
        }
    }

    private Slice handleBindSlice(final Uri sliceUri, final Set<SliceSpec> specs,
            final String callingPkg) {
        // This can be removed once Slice#bindSlice is removed and everyone is using
        // SliceManager#bindSlice.
        String pkg = callingPkg != null ? callingPkg
                : getContext().getPackageManager().getNameForUid(Binder.getCallingUid());
        if (Binder.getCallingUid() != Process.myUid()) {
            try {
                getContext().enforceUriPermission(sliceUri,
                        Binder.getCallingPid(), Binder.getCallingUid(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        "Slice binding requires write access to Uri");
            } catch (SecurityException e) {
                return createPermissionSlice(getContext(), sliceUri, pkg);
            }
        }
        return onBindSliceStrict(sliceUri, specs);
    }

    /**
     * Generate a slice that contains a permission request.
     */
    public static Slice createPermissionSlice(Context context, Uri sliceUri,
            String callingPackage) {
        Slice.Builder parent = new Slice.Builder(sliceUri);

        Slice.Builder action = new Slice.Builder(parent)
                .addHints(HINT_TITLE, HINT_SHORTCUT)
                .addAction(createPermissionIntent(context, sliceUri, callingPackage),
                        new Slice.Builder(parent).build(), null);

        parent.addSubSlice(new Slice.Builder(sliceUri.buildUpon().appendPath("permission").build())
                .addText(getPermissionString(context, callingPackage), null)
                .addSubSlice(action.build())
                .build());

        return parent.addHints(HINT_PERMISSION_REQUEST).build();
    }

    /**
     * Create a PendingIntent pointing at the permission dialog.
     */
    public static PendingIntent createPermissionIntent(Context context, Uri sliceUri,
            String callingPackage) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(),
                "androidx.slice.compat.SlicePermissionActivity"));
        intent.putExtra(EXTRA_BIND_URI, sliceUri);
        intent.putExtra(EXTRA_PKG, callingPackage);
        intent.putExtra(EXTRA_PROVIDER_PKG, context.getPackageName());
        // Unique pending intent.
        intent.setData(sliceUri.buildUpon().appendQueryParameter("package", callingPackage)
                .build());

        return PendingIntent.getActivity(context, 0, intent, 0);
    }

    /**
     * Get string describing permission request.
     */
    public static CharSequence getPermissionString(Context context, String callingPackage) {
        PackageManager pm = context.getPackageManager();
        try {
            return context.getString(R.string.abc_slices_permission_request,
                    pm.getApplicationInfo(callingPackage, 0).loadLabel(pm),
                    context.getApplicationInfo().loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            // This shouldn't be possible since the caller is verified.
            throw new RuntimeException("Unknown calling app", e);
        }
    }

    private Slice onBindSliceStrict(Uri sliceUri, Set<SliceSpec> specs) {
        ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        mCallback = "onBindSlice";
        mHandler.postDelayed(mAnr, SLICE_BIND_ANR);
        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyDeath()
                    .build());
            SliceProvider.setSpecs(specs);
            try {
                return mSliceProvider.onBindSlice(sliceUri);
            } finally {
                SliceProvider.setSpecs(null);
                mHandler.removeCallbacks(mAnr);
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    /**
     * Compat version of {@link Slice#bindSlice}.
     */
    public static Slice bindSlice(Context context, Uri uri,
            Set<SliceSpec> supportedSpecs) {
        ContentProviderClient provider = context.getContentResolver()
                .acquireContentProviderClient(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_BIND_URI, uri);
            addSpecs(extras, supportedSpecs);
            final Bundle res = provider.call(METHOD_SLICE, null, extras);
            if (res == null) {
                return null;
            }
            Parcelable bundle = res.getParcelable(EXTRA_SLICE);
            if (!(bundle instanceof Bundle)) {
                return null;
            }
            return new Slice((Bundle) bundle);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
            return null;
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                provider.close();
            } else {
                provider.release();
            }
        }
    }

    private static void addSpecs(Bundle extras, Set<SliceSpec> supportedSpecs) {
        ArrayList<String> types = new ArrayList<>();
        ArrayList<Integer> revs = new ArrayList<>();
        for (SliceSpec spec : supportedSpecs) {
            types.add(spec.getType());
            revs.add(spec.getRevision());
        }
        extras.putStringArrayList(EXTRA_SUPPORTED_SPECS, types);
        extras.putIntegerArrayList(EXTRA_SUPPORTED_SPECS_REVS, revs);
    }

    private static Set<SliceSpec> getSpecs(Bundle extras) {
        ArraySet<SliceSpec> specs = new ArraySet<>();
        ArrayList<String> types = extras.getStringArrayList(EXTRA_SUPPORTED_SPECS);
        ArrayList<Integer> revs = extras.getIntegerArrayList(EXTRA_SUPPORTED_SPECS_REVS);
        for (int i = 0; i < types.size(); i++) {
            specs.add(new SliceSpec(types.get(i), revs.get(i)));
        }
        return specs;
    }

    /**
     * Compat version of {@link Slice#bindSlice}.
     */
    public static Slice bindSlice(Context context, Intent intent,
            Set<SliceSpec> supportedSpecs) {
        ContentResolver resolver = context.getContentResolver();

        // Check if the intent has data for the slice uri on it and use that
        final Uri intentData = intent.getData();
        if (intentData != null && SLICE_TYPE.equals(resolver.getType(intentData))) {
            return bindSlice(context, intentData, supportedSpecs);
        }
        // Otherwise ask the app
        List<ResolveInfo> providers =
                context.getPackageManager().queryIntentContentProviders(intent, 0);
        if (providers == null) {
            throw new IllegalArgumentException("Unable to resolve intent " + intent);
        }
        String authority = providers.get(0).providerInfo.authority;
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority).build();
        ContentProviderClient provider = resolver.acquireContentProviderClient(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_INTENT, intent);
            addSpecs(extras, supportedSpecs);
            final Bundle res = provider.call(METHOD_MAP_INTENT, null, extras);
            if (res == null) {
                return null;
            }
            Parcelable bundle = res.getParcelable(EXTRA_SLICE);
            if (!(bundle instanceof Bundle)) {
                return null;
            }
            return new Slice((Bundle) bundle);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
            return null;
        } finally {
            provider.close();
        }
    }

    /**
     * Compat version of {@link android.app.slice.SliceManager#pinSlice}.
     */
    public static void pinSlice(Context context, Uri uri,
            Set<SliceSpec> supportedSpecs) {
        ContentProviderClient provider = context.getContentResolver()
                .acquireContentProviderClient(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_BIND_URI, uri);
            extras.putString(EXTRA_PKG, context.getPackageName());
            addSpecs(extras, supportedSpecs);
            provider.call(METHOD_PIN, null, extras);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
        } finally {
            provider.close();
        }
    }

    /**
     * Compat version of {@link android.app.slice.SliceManager#unpinSlice}.
     */
    public static void unpinSlice(Context context, Uri uri,
            Set<SliceSpec> supportedSpecs) {
        ContentProviderClient provider = context.getContentResolver()
                .acquireContentProviderClient(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_BIND_URI, uri);
            extras.putString(EXTRA_PKG, context.getPackageName());
            addSpecs(extras, supportedSpecs);
            provider.call(METHOD_UNPIN, null, extras);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
        } finally {
            provider.close();
        }
    }

    /**
     * Compat version of {@link android.app.slice.SliceManager#getPinnedSpecs(Uri)}.
     */
    public static Set<SliceSpec> getPinnedSpecs(Context context, Uri uri) {
        ContentProviderClient provider = context.getContentResolver()
                .acquireContentProviderClient(uri);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_BIND_URI, uri);
            final Bundle res = provider.call(METHOD_GET_PINNED_SPECS, null, extras);
            if (res == null) {
                return null;
            }
            return getSpecs(res);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
            return null;
        } finally {
            provider.close();
        }
    }

    /**
     * Compat version of {@link android.app.slice.SliceManager#mapIntentToUri}.
     */
    public static Uri mapIntentToUri(Context context, Intent intent) {
        Preconditions.checkNotNull(intent, "intent");
        Preconditions.checkArgument(intent.getComponent() != null || intent.getPackage() != null,
                String.format("Slice intent must be explicit %s", intent));
        ContentResolver resolver = context.getContentResolver();

        // Check if the intent has data for the slice uri on it and use that
        final Uri intentData = intent.getData();
        if (intentData != null && SLICE_TYPE.equals(resolver.getType(intentData))) {
            return intentData;
        }
        // Otherwise ask the app
        List<ResolveInfo> providers =
                context.getPackageManager().queryIntentContentProviders(intent, 0);
        if (providers == null || providers.isEmpty()) {
            // There are no providers, see if this activity has a direct link.
            ResolveInfo resolve = context.getPackageManager().resolveActivity(intent,
                    PackageManager.GET_META_DATA);
            if (resolve != null && resolve.activityInfo != null
                    && resolve.activityInfo.metaData != null
                    && resolve.activityInfo.metaData.containsKey(SliceHints.SLICE_METADATA_KEY)) {
                return Uri.parse(
                        resolve.activityInfo.metaData.getString(SliceHints.SLICE_METADATA_KEY));
            }
            return null;
        }
        String authority = providers.get(0).providerInfo.authority;
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority).build();
        try (ContentProviderClient provider = resolver.acquireContentProviderClient(uri)) {
            if (provider == null) {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
            Bundle extras = new Bundle();
            extras.putParcelable(EXTRA_INTENT, intent);
            final Bundle res = provider.call(METHOD_MAP_ONLY_INTENT, null, extras);
            if (res == null) {
                return null;
            }
            return res.getParcelable(EXTRA_SLICE);
        } catch (RemoteException e) {
            // Arbitrary and not worth documenting, as Activity
            // Manager will kill this process shortly anyway.
            return null;
        }
    }

    private final Runnable mAnr = new Runnable() {
        @Override
        public void run() {
            Process.sendSignal(Process.myPid(), Process.SIGNAL_QUIT);
            Log.wtf(TAG, "Timed out while handling slice callback " + mCallback);
        }
    };
}
