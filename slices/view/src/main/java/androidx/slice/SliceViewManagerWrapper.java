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

package androidx.slice;

import static androidx.slice.SliceConvert.unwrap;
import static androidx.slice.widget.SliceLiveData.SUPPORTED_SPECS;

import android.app.slice.SliceSpec;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import java.util.Collection;
import java.util.Set;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(api = 28)
class SliceViewManagerWrapper extends SliceViewManagerBase {

    private final android.app.slice.SliceManager mManager;
    private final Set<SliceSpec> mSpecs;

    SliceViewManagerWrapper(Context context) {
        this(context, context.getSystemService(android.app.slice.SliceManager.class));
    }

    SliceViewManagerWrapper(Context context, android.app.slice.SliceManager manager) {
        super(context);
        mManager = manager;
        mSpecs = unwrap(SUPPORTED_SPECS);
    }

    @Override
    public void pinSlice(@NonNull Uri uri) {
        mManager.pinSlice(uri, mSpecs);
    }

    @Override
    public void unpinSlice(@NonNull Uri uri) {
        if (mManager.getPinnedSlices().contains(uri)) {
            mManager.unpinSlice(uri);
        }
    }

    @Nullable
    @Override
    public androidx.slice.Slice bindSlice(@NonNull Uri uri) {
        return SliceConvert.wrap(mManager.bindSlice(uri, mSpecs), mContext);
    }

    @Nullable
    @Override
    public androidx.slice.Slice bindSlice(@NonNull Intent intent) {
        return SliceConvert.wrap(mManager.bindSlice(intent, mSpecs), mContext);
    }

    @Override
    public Collection<Uri> getSliceDescendants(Uri uri) {
        // TODO: When this is fixed in framework, remove this try / catch (b/80118259)
        try {
            return mManager.getSliceDescendants(uri);
        } catch (RuntimeException e) {
            // Check if a provider exists for this uri
            ContentResolver resolver = mContext.getContentResolver();
            ContentProviderClient provider = resolver.acquireContentProviderClient(uri);
            if (provider == null) {
                throw new IllegalArgumentException("No provider found for " + uri);
            } else {
                provider.close();
                throw e;
            }
        }
    }

    @Nullable
    @Override
    public Uri mapIntentToUri(@NonNull Intent intent) {
        return mManager.mapIntentToUri(intent);
    }
}
