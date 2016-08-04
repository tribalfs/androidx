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

package android.support.v4.os;

import android.os.Parcelable;

/**
 * Helper for accessing features in {@link android.os.Parcelable}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public final class ParcelableCompat {
    /**
     * Factory method for {@link Parcelable.Creator}.
     *
     * @param callbacks Creator callbacks implementation.
     * @return New creator.
     */
    public static <T> Parcelable.Creator<T> newCreator(
            ParcelableCompatCreatorCallbacks<T> callbacks) {
        if (android.os.Build.VERSION.SDK_INT >= 13) {
            return new ParcelableCompatCreatorHoneycombMR2<T>(callbacks);
        }
        return new ParcelableCompatCreatorBase<T>(callbacks);
    }

    private ParcelableCompat() {}
}
