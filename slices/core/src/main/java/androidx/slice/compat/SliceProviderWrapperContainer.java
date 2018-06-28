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

package androidx.slice.compat;

import static androidx.slice.SliceConvert.wrap;

import android.app.slice.Slice;
import android.app.slice.SliceProvider;
import android.app.slice.SliceSpec;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.slice.SliceConvert;

import java.util.Collection;
import java.util.Set;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SliceProviderWrapperContainer {

    /**
     */
    @RequiresApi(28)
    public static class SliceProviderWrapper extends SliceProvider {
        private static final String TAG = "SliceProviderWrapper";

        private androidx.slice.SliceProvider mSliceProvider;

        public SliceProviderWrapper(androidx.slice.SliceProvider provider,
                String[] autoGrantPermissions) {
            super(autoGrantPermissions);
            mSliceProvider = provider;
        }

        @Override
        public void attachInfo(Context context, ProviderInfo info) {
            mSliceProvider.attachInfo(context, info);
            super.attachInfo(context, info);
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Slice onBindSlice(Uri sliceUri, Set<SliceSpec> supportedVersions) {
            androidx.slice.SliceProvider.setSpecs(wrap(supportedVersions));
            try {
                return SliceConvert.unwrap(mSliceProvider.onBindSlice(sliceUri));
            } catch (Exception e) {
                Log.wtf(TAG, "Slice with URI " + sliceUri.toString() + " is invalid.", e);
                return null;
            } finally {
                androidx.slice.SliceProvider.setSpecs(null);
            }
        }

        @Override
        public void onSlicePinned(Uri sliceUri) {
            mSliceProvider.onSlicePinned(sliceUri);
            mSliceProvider.handleSlicePinned(sliceUri);
        }

        @Override
        public void onSliceUnpinned(Uri sliceUri) {
            mSliceProvider.onSliceUnpinned(sliceUri);
            mSliceProvider.handleSliceUnpinned(sliceUri);
        }

        @Override
        public Collection<Uri> onGetSliceDescendants(Uri uri) {
            return mSliceProvider.onGetSliceDescendants(uri);
        }

        /**
         * Maps intents to uris.
         */
        @Override
        public @NonNull Uri onMapIntentToUri(Intent intent) {
            return mSliceProvider.onMapIntentToUri(intent);
        }
    }

    private SliceProviderWrapperContainer() {
    }
}
