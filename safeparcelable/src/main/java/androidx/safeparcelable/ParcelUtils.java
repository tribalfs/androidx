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

package androidx.safeparcelable;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.os.Parcelable;

import androidx.annotation.RestrictTo;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class ParcelUtils {

    private ParcelUtils() { }

    /**
     * Turn a SafeParcelable into a Parcelable
     */
    public static Parcelable toParcelable(SafeParcelable obj) {
        return new ParcelImpl(obj);
    }

    /**
     * Turn a Parcelable into a SafeParcelable.
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T extends SafeParcelable> T fromParcelable(Parcelable p) {
        if (!(p instanceof ParcelImpl)) {
            throw new IllegalArgumentException("Invalid parcel");
        }
        return ((ParcelImpl) p).getSafeParcel();
    }

    /**
     * Write a SafeParcelable into an OutputStream.
     */
    public static void toOutputStream(SafeParcelable obj, OutputStream output) {
        SafeParcelStream stream = new SafeParcelStream(null, output);
        stream.writeSafeParcelable(obj);
        stream.closeField();
    }

    /**
     * Read a SafeParcelable from an InputStream.
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T extends SafeParcelable> T fromInputStream(InputStream input) {
        SafeParcelStream stream = new SafeParcelStream(input, null);
        return stream.readSafeParcelable();
    }
}
