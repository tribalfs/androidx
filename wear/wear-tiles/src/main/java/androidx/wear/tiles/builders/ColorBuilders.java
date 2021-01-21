/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.tiles.builders;

import android.annotation.SuppressLint;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.tiles.proto.ColorProto;

/** Builders for color utilities for layout elements. */
public final class ColorBuilders {
    private ColorBuilders() {}

    /** Shortcut for building a {@link ColorProp} using an ARGB value. */
    @NonNull
    public static ColorProp argb(@ColorInt int colorArgb) {
        return ColorProp.builder().setArgb(colorArgb).build();
    }

    /** A property defining a color. */
    public static final class ColorProp {
        private final ColorProto.ColorProp mImpl;

        ColorProp(ColorProto.ColorProp impl) {
            this.mImpl = impl;
        }

        /** Returns a new {@link Builder}. */
        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Get the protocol buffer representation of this object.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY)
        @NonNull
        public ColorProto.ColorProp toProto() {
            return mImpl;
        }

        /** Builder for {@link ColorProp} */
        public static final class Builder {
            private final ColorProto.ColorProp.Builder mImpl = ColorProto.ColorProp.newBuilder();

            Builder() {}

            /** Sets the color value, in ARGB format. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setArgb(@ColorInt int argb) {
                mImpl.setArgb(argb);
                return this;
            }

            /** Builds an instance from accumulated values. */
            @NonNull
            public ColorProp build() {
                return new ColorProp(mImpl.build());
            }
        }
    }
}
