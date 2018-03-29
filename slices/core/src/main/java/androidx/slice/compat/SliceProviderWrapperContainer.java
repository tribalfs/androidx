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

import android.annotation.TargetApi;
import android.app.slice.Slice;
import android.app.slice.SliceProvider;
import android.app.slice.SliceSpec;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.collection.ArraySet;
import androidx.slice.SliceConvert;

import java.util.List;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@TargetApi(28)
public class SliceProviderWrapperContainer {

    /**
     */
    public static class SliceProviderWrapper extends SliceProvider {

        private androidx.slice.SliceProvider mSliceProvider;

        public SliceProviderWrapper(androidx.slice.SliceProvider provider) {
            mSliceProvider = provider;
        }

        @Override
        public boolean onCreate() {
            return mSliceProvider.onCreateSliceProvider();
        }

        @Override
        public Slice onBindSlice(Uri sliceUri, List<SliceSpec> supportedVersions) {
            androidx.slice.SliceProvider.setSpecs(new ArraySet<>(wrap(supportedVersions)));
            try {
                return SliceConvert.unwrap(mSliceProvider.onBindSlice(sliceUri));
            } finally {
                androidx.slice.SliceProvider.setSpecs(null);
            }
        }

        @Override
        public void onSlicePinned(Uri sliceUri) {
            mSliceProvider.onSlicePinned(sliceUri);
        }

        @Override
        public void onSliceUnpinned(Uri sliceUri) {
            mSliceProvider.onSliceUnpinned(sliceUri);
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
