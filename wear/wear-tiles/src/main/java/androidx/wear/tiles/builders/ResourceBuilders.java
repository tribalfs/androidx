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

import static androidx.annotation.Dimension.PX;

import android.annotation.SuppressLint;

import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.tiles.proto.ResourceProto;
import androidx.wear.tiles.protobuf.ByteString;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Builders for the resources for a layout. */
public final class ResourceBuilders {
    private ResourceBuilders() {}

    /**
     * Format describing the contents of an image data byte array.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef({IMAGE_FORMAT_UNDEFINED, IMAGE_FORMAT_RGB_565})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImageFormat {}

    /** An undefined image format. */
    public static final int IMAGE_FORMAT_UNDEFINED = 0;

    /**
     * An image format where each pixel is stored on 2 bytes, with red using 5 bits, green using 6
     * bits and blue using 5 bits of precision.
     */
    public static final int IMAGE_FORMAT_RGB_565 = 1;

    /** An image resource which maps to an Android drawable by resource ID. */
    public static final class AndroidImageResourceByResId {
        private final ResourceProto.AndroidImageResourceByResId mImpl;

        private AndroidImageResourceByResId(ResourceProto.AndroidImageResourceByResId impl) {
            this.mImpl = impl;
        }

        /** Returns a new {@link Builder}. */
        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static AndroidImageResourceByResId fromProto(
                @NonNull ResourceProto.AndroidImageResourceByResId proto) {
            return new AndroidImageResourceByResId(proto);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public ResourceProto.AndroidImageResourceByResId toProto() {
            return mImpl;
        }

        /** Builder for {@link AndroidImageResourceByResId} */
        public static final class Builder {
            private final ResourceProto.AndroidImageResourceByResId.Builder mImpl =
                    ResourceProto.AndroidImageResourceByResId.newBuilder();

            Builder() {}

            /**
             * Sets the Android resource ID of this image. This must refer to a drawable under
             * R.drawable.
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setResourceId(@DrawableRes int resourceId) {
                mImpl.setResourceId(resourceId);
                return this;
            }

            /** Builds an instance from accumulated values. */
            @NonNull
            public AndroidImageResourceByResId build() {
                return AndroidImageResourceByResId.fromProto(mImpl.build());
            }
        }
    }

    /**
     * An image resource whose data is fully inlined, with no dependency on a system or app
     * resource.
     */
    public static final class InlineImageResource {
        private final ResourceProto.InlineImageResource mImpl;

        private InlineImageResource(ResourceProto.InlineImageResource impl) {
            this.mImpl = impl;
        }

        /** Returns a new {@link Builder}. */
        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static InlineImageResource fromProto(
                @NonNull ResourceProto.InlineImageResource proto) {
            return new InlineImageResource(proto);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public ResourceProto.InlineImageResource toProto() {
            return mImpl;
        }

        /** Builder for {@link InlineImageResource} */
        public static final class Builder {
            private final ResourceProto.InlineImageResource.Builder mImpl =
                    ResourceProto.InlineImageResource.newBuilder();

            Builder() {}

            /** Sets the byte array representing the image. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setData(@NonNull byte[] data) {
                mImpl.setData(ByteString.copyFrom(data));
                return this;
            }

            /**
             * Sets the native width of the image, in pixels. Only required for formats (e.g.
             * IMAGE_FORMAT_RGB_565) where the image data does not include size.
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setWidthPx(@Dimension(unit = PX) int widthPx) {
                mImpl.setWidthPx(widthPx);
                return this;
            }

            /**
             * Sets the native height of the image, in pixels. Only required for formats (e.g.
             * IMAGE_FORMAT_RGB_565) where the image data does not include size.
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setHeightPx(@Dimension(unit = PX) int heightPx) {
                mImpl.setHeightPx(heightPx);
                return this;
            }

            /**
             * Sets the format of the byte array data representing the image. May be left
             * unspecified or set to IMAGE_FORMAT_UNDEFINED in which case the platform will attempt
             * to extract this from the raw image data. If the platform does not support the format,
             * the image will not be decoded or displayed.
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setFormat(@ImageFormat int format) {
                mImpl.setFormat(ResourceProto.ImageFormat.forNumber(format));
                return this;
            }

            /** Builds an instance from accumulated values. */
            @NonNull
            public InlineImageResource build() {
                return InlineImageResource.fromProto(mImpl.build());
            }
        }
    }

    /**
     * An image resource, which can be used by layouts. This holds multiple underlying resource
     * types, which the underlying runtime will pick according to what it thinks is appropriate.
     */
    public static final class ImageResource {
        private final ResourceProto.ImageResource mImpl;

        private ImageResource(ResourceProto.ImageResource impl) {
            this.mImpl = impl;
        }

        /** Returns a new {@link Builder}. */
        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static ImageResource fromProto(@NonNull ResourceProto.ImageResource proto) {
            return new ImageResource(proto);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public ResourceProto.ImageResource toProto() {
            return mImpl;
        }

        /** Builder for {@link ImageResource} */
        public static final class Builder {
            private final ResourceProto.ImageResource.Builder mImpl =
                    ResourceProto.ImageResource.newBuilder();

            Builder() {}

            /** Sets an image resource that maps to an Android drawable by resource ID. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setAndroidResourceByResid(
                    @NonNull AndroidImageResourceByResId androidResourceByResid) {
                mImpl.setAndroidResourceByResid(androidResourceByResid.toProto());
                return this;
            }

            /** Sets an image resource that maps to an Android drawable by resource ID. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setAndroidResourceByResid(
                    @NonNull AndroidImageResourceByResId.Builder androidResourceByResidBuilder) {
                mImpl.setAndroidResourceByResid(androidResourceByResidBuilder.build().toProto());
                return this;
            }

            /** Sets an image resource that contains the image data inline. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setInlineResource(@NonNull InlineImageResource inlineResource) {
                mImpl.setInlineResource(inlineResource.toProto());
                return this;
            }

            /** Sets an image resource that contains the image data inline. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setInlineResource(
                    @NonNull InlineImageResource.Builder inlineResourceBuilder) {
                mImpl.setInlineResource(inlineResourceBuilder.build().toProto());
                return this;
            }

            /** Builds an instance from accumulated values. */
            @NonNull
            public ImageResource build() {
                return ImageResource.fromProto(mImpl.build());
            }
        }
    }

    /** The resources for a layout. */
    public static final class Resources {
        private final ResourceProto.Resources mImpl;

        private Resources(ResourceProto.Resources impl) {
            this.mImpl = impl;
        }

        /** Returns a new {@link Builder}. */
        @NonNull
        public static Builder builder() {
            return new Builder();
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static Resources fromProto(@NonNull ResourceProto.Resources proto) {
            return new Resources(proto);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public ResourceProto.Resources toProto() {
            return mImpl;
        }

        /** Builder for {@link Resources} */
        public static final class Builder {
            private final ResourceProto.Resources.Builder mImpl =
                    ResourceProto.Resources.newBuilder();

            Builder() {}

            /**
             * Sets the version of this {@link Resources} instance.
             *
             * <p>Each tile specifies the version of resources it requires. After fetching a tile,
             * the renderer will use the resources version specified by the tile to separately fetch
             * the resources.
             *
             * <p>This value must match the version of the resources required by the tile for the
             * tile to render successfully.
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setVersion(@NonNull String version) {
                mImpl.setVersion(version);
                return this;
            }

            /** Adds an entry into a map of resource_ids to images, which can be used by layouts. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder addIdToImageMapping(@NonNull String id, @NonNull ImageResource image) {
                mImpl.putIdToImage(id, image.toProto());
                return this;
            }

            /** Adds an entry into a map of resource_ids to images, which can be used by layouts. */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder addIdToImageMapping(
                    @NonNull String id, @NonNull ImageResource.Builder imageBuilder) {
                mImpl.putIdToImage(id, imageBuilder.build().toProto());
                return this;
            }

            /** Builds an instance from accumulated values. */
            @NonNull
            public Resources build() {
                return Resources.fromProto(mImpl.build());
            }
        }
    }
}
