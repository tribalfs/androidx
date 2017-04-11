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

package android.support.v4.graphics;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.support.v4.graphics.fonts.FontRequest;

/**
 * Implementation of the Typeface compat methods for API 26 and above.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
@RequiresApi(26)
class TypefaceCompatApi26Impl implements TypefaceCompat.TypefaceCompatImpl {

    public void create(@NonNull final FontRequest request,
            @NonNull final TypefaceCompat.FontRequestCallback callback) {
        Typeface.create(new android.graphics.fonts.FontRequest(request.getProviderAuthority(),
                request.getProviderPackage(), request.getQuery(), request.getCertificates()),
                new Typeface.FontRequestCallback() {
                    @Override
                    public void onTypefaceRetrieved(Typeface typeface) {
                        callback.onTypefaceRetrieved(typeface);
                    }

                    @Override
                    public void onTypefaceRequestFailed(int reason) {
                        callback.onTypefaceRequestFailed(reason);
                    }
                });
    }
}
