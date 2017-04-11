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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.v4.graphics.fonts.FontRequest;
import android.support.v4.provider.FontsContract;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Helper for accessing features in {@link Typeface} in a backwards compatible fashion.
 */
public class TypefaceCompat {

    private static TypefaceCompatImpl sTypefaceCompatImpl;
    private static final Object sLock = new Object();

    /**
     * Create a typeface object given a font request. The font will be asynchronously fetched,
     * therefore the result is delivered to the given callback. See {@link FontRequest}.
     * Only one of the methods in callback will be invoked, depending on whether the request
     * succeeds or fails. These calls will happen on the main thread.
     * @param request A {@link FontRequest} object that identifies the provider and query for the
     *                request. May not be null.
     * @param callback A callback that will be triggered when results are obtained. May not be null.
     */
    @TargetApi(26)
    public static void create(Context context, @NonNull final FontRequest request,
            @NonNull final FontRequestCallback callback) {
        synchronized (sLock) {
            if (sTypefaceCompatImpl == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sTypefaceCompatImpl = new TypefaceCompatApi26Impl();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sTypefaceCompatImpl = new TypefaceCompatApi24Impl(context);
                } else {
                    sTypefaceCompatImpl = new TypefaceCompatBaseImpl(context);
                }
            }
        }
        sTypefaceCompatImpl.create(request, callback);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    interface TypefaceCompatImpl {
        /**
         * Create a typeface object given a font request. The font will be asynchronously fetched,
         * therefore the result is delivered to the given callback. See {@link FontRequest}.
         * Only one of the methods in callback will be invoked, depending on whether the request
         * succeeds or fails. These calls will happen on the main thread.
         * @param request A {@link FontRequest} object that identifies the provider and query for
         *                the request. May not be null.
         * @param callback A callback that will be triggered when results are obtained. May not be
         *                 null.
         */
        void create(@NonNull FontRequest request,
                @NonNull TypefaceCompat.FontRequestCallback callback);
    }

    /**
     * Interface used to receive asynchronously fetched typefaces.
     */
    public abstract static class FontRequestCallback {
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the given
         * provider was not found on the device.
         */
        public static final int FAIL_REASON_PROVIDER_NOT_FOUND =
                FontsContract.RESULT_CODE_PROVIDER_NOT_FOUND;
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the given
         * provider must be authenticated and the given certificates do not match its signature.
         */
        public static final int FAIL_REASON_WRONG_CERTIFICATES =
                FontsContract.RESULT_CODE_WRONG_CERTIFICATES;
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the font
         * returned by the provider was not loaded properly.
         */
        public static final int FAIL_REASON_FONT_LOAD_ERROR = -3;
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the font
         * provider did not return any results for the given query.
         */
        public static final int FAIL_REASON_FONT_NOT_FOUND =
                FontsContract.Columns.RESULT_CODE_FONT_NOT_FOUND;
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the font
         * provider found the queried font, but it is currently unavailable.
         */
        public static final int FAIL_REASON_FONT_UNAVAILABLE =
                FontsContract.Columns.RESULT_CODE_FONT_UNAVAILABLE;
        /**
         * Constant returned by {@link #onTypefaceRequestFailed(int)} signaling that the given
         * query was not supported by the provider.
         */
        public static final int FAIL_REASON_MALFORMED_QUERY =
                FontsContract.Columns.RESULT_CODE_MALFORMED_QUERY;

        /** @hide */
        @RestrictTo(LIBRARY_GROUP)
        @IntDef({ FAIL_REASON_PROVIDER_NOT_FOUND, FAIL_REASON_FONT_LOAD_ERROR,
                FAIL_REASON_FONT_NOT_FOUND, FAIL_REASON_FONT_UNAVAILABLE,
                FAIL_REASON_MALFORMED_QUERY })
        @Retention(RetentionPolicy.SOURCE)
        @interface FontRequestFailReason {}

        /**
         * Called then a Typeface request done via {@link TypefaceCompat#create(Context,
         * FontRequest, FontRequestCallback)} is complete. Note that this method will not be called
         * if {@link #onTypefaceRequestFailed(int)} is called instead.
         * @param typeface  The Typeface object retrieved.
         */
        public abstract void onTypefaceRetrieved(Typeface typeface);

        /**
         * Called when a Typeface request done via {@link TypefaceCompat#create(Context,
         * FontRequest, FontRequestCallback)} fails.
         * @param reason One of {@link #FAIL_REASON_PROVIDER_NOT_FOUND},
         *               {@link #FAIL_REASON_FONT_NOT_FOUND},
         *               {@link #FAIL_REASON_FONT_LOAD_ERROR},
         *               {@link #FAIL_REASON_FONT_UNAVAILABLE} or
         *               {@link #FAIL_REASON_MALFORMED_QUERY}.
         */
        public abstract void onTypefaceRequestFailed(@FontRequestFailReason int reason);
    }

    private TypefaceCompat() {}
}
