/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface.style.data;

import android.graphics.drawable.Icon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.versionedparcelable.ParcelField;
import androidx.versionedparcelable.VersionedParcelize;

import java.util.List;

/**
 * Wire format for {@link androidx.wear.watchface.style.ListUserStyleCategory}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
@VersionedParcelize
public class ListUserStyleCategoryWireFormat extends UserStyleCategoryWireFormat {

    ListUserStyleCategoryWireFormat() {}

    public ListUserStyleCategoryWireFormat(
            @NonNull String id,
            @NonNull String displayName,
            @NonNull String description,
            @Nullable Icon icon,
            @NonNull List<OptionWireFormat> options,
            int defaultOptionIndex,
            @NonNull List<Integer> affectsLayers) {
        super(id, displayName, description, icon, options, defaultOptionIndex, affectsLayers);
    }

    /**
     * Wire format for
     * {@link androidx.wear.watchface.style.ListUserStyleCategory.ListOption}.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @VersionedParcelize
    public static class ListOptionWireFormat extends OptionWireFormat {
        /** Localized human readable name for the setting, used in the style selection UI. */
        @ParcelField(2)
        @NonNull
        public String mDisplayName = "";

        /** Icon for use in the style selection UI. */
        @ParcelField(3)
        @Nullable
        public Icon mIcon = null;

        ListOptionWireFormat() {}

        public ListOptionWireFormat(
                @NonNull String id,
                @NonNull String displayName,
                @Nullable Icon icon
        ) {
            super(id);
            this.mDisplayName = displayName;
            this.mIcon = icon;
        }
    }
}
