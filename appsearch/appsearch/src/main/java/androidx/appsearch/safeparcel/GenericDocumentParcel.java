/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appsearch.safeparcel;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.appsearch.annotation.CanIgnoreReturnValue;
import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.safeparcel.stub.StubCreators.GenericDocumentParcelCreator;
import androidx.collection.ArrayMap;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Holds data for a {@link GenericDocument}.
 *
 * @exportToFramework:hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SafeParcelable.Class(creator = "GenericDocumentParcelCreator")
public final class GenericDocumentParcel extends AbstractSafeParcelable {
    @NonNull
    public static final GenericDocumentParcelCreator CREATOR =
            new GenericDocumentParcelCreator();

    /** The default score of document. */
    private static final int DEFAULT_SCORE = 0;

    /** The default time-to-live in millisecond of a document, which is infinity. */
    private static final long DEFAULT_TTL_MILLIS = 0L;

    /** Default but invalid value for {@code mCreationTimestampMillis}. */
    private static final long INVALID_CREATION_TIMESTAMP_MILLIS = -1L;

    @Field(id = 1, getter = "getNamespace")
    @NonNull
    private final String mNamespace;

    @Field(id = 2, getter = "getId")
    @NonNull
    private final String mId;

    @Field(id = 3, getter = "getSchemaType")
    @NonNull
    private final String mSchemaType;

    @Field(id = 4, getter = "getCreationTimestampMillis")
    private final long mCreationTimestampMillis;

    @Field(id = 5, getter = "getTtlMillis")
    private final long mTtlMillis;

    @Field(id = 6, getter = "getScore")
    private final int mScore;

    /**
     * Contains all properties in {@link GenericDocument} in a list.
     *
     * <p>Unfortunately SafeParcelable doesn't support map type so we have to use a list here.
     */
    @Field(id = 7, getter = "getProperties")
    @NonNull
    private final PropertyParcel[] mProperties;

    /**
     * Contains all properties in {@link GenericDocument} to support getting properties via name
     *
     * <p>This map is created for quick looking up property by name.
     */
    @NonNull
    private final Map<String, PropertyParcel> mPropertyMap;

    @Nullable
    private Integer mHashCode;

    /**
     * The constructor taking the property list, and create map internally from this list.
     *
     * <p> This will be used in createFromParcel, so creating the property map can not be avoided
     * in this constructor.
     */
    @Constructor
    GenericDocumentParcel(
            @Param(id = 1) @NonNull String namespace,
            @Param(id = 2) @NonNull String id,
            @Param(id = 3) @NonNull String schemaType,
            @Param(id = 4) long creationTimestampMillis,
            @Param(id = 5) long ttlMillis,
            @Param(id = 6) int score,
            @Param(id = 7) @NonNull PropertyParcel[] properties) {
        this(namespace, id, schemaType, creationTimestampMillis, ttlMillis, score,
                properties, createPropertyMapFromPropertyArray(properties));
    }

    /**
     * A constructor taking both property list and property map.
     *
     * <p>Caller needs to make sure property list and property map
     * matches(map is generated from list, or list generated from map).
     */
    GenericDocumentParcel(
            @NonNull String namespace,
            @NonNull String id,
            @NonNull String schemaType,
            long creationTimestampMillis,
            long ttlMillis,
            int score,
            @NonNull PropertyParcel[] properties,
            @NonNull Map<String, PropertyParcel> propertyMap) {
        mNamespace = Objects.requireNonNull(namespace);
        mId = Objects.requireNonNull(id);
        mSchemaType = Objects.requireNonNull(schemaType);
        mCreationTimestampMillis = creationTimestampMillis;
        mTtlMillis = ttlMillis;
        mScore = score;
        mProperties = Objects.requireNonNull(properties);
        mPropertyMap = Objects.requireNonNull(propertyMap);
    }

    private static Map<String, PropertyParcel> createPropertyMapFromPropertyArray(
            @NonNull PropertyParcel[] properties) {
        Objects.requireNonNull(properties);
        Map<String, PropertyParcel> propertyMap = new ArrayMap<>(properties.length);
        for (int i = 0; i < properties.length; ++i) {
            PropertyParcel property = properties[i];
            propertyMap.put(property.getPropertyName(), property);
        }
        return propertyMap;
    }

    /** Returns the unique identifier of the {@link GenericDocument}. */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the namespace of the {@link GenericDocument}. */
    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    /** Returns the {@link AppSearchSchema} type of the {@link GenericDocument}. */
    @NonNull
    public String getSchemaType() {
        return mSchemaType;
    }

    /** Returns the creation timestamp of the {@link GenericDocument}, in milliseconds. */
    /*@exportToFramework:CurrentTimeMillisLong*/
    public long getCreationTimestampMillis() {
        return mCreationTimestampMillis;
    }

    /** Returns the TTL (time-to-live) of the {@link GenericDocument}, in milliseconds. */
    public long getTtlMillis() {
        return mTtlMillis;
    }

    /** Returns the score of the {@link GenericDocument}. */
    public int getScore() {
        return mScore;
    }

    /** Returns the names of all properties defined in this document. */
    @NonNull
    public Set<String> getPropertyNames() {
        return mPropertyMap.keySet();
    }

    /** Returns all the properties the document has. */
    @NonNull
    public PropertyParcel[] getProperties() {
        return mProperties;
    }

    /** Returns the property map the document has. */
    @NonNull
    public Map<String, PropertyParcel> getPropertyMap() {
        return mPropertyMap;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenericDocumentParcel)) {
            return false;
        }
        GenericDocumentParcel otherDocument = (GenericDocumentParcel) other;
        return mNamespace.equals(otherDocument.mNamespace)
                && mId.equals(otherDocument.mId)
                && mSchemaType.equals(otherDocument.mSchemaType)
                && mTtlMillis == otherDocument.mTtlMillis
                && mCreationTimestampMillis == otherDocument.mCreationTimestampMillis
                && mScore == otherDocument.mScore
                && Arrays.equals(mProperties, otherDocument.mProperties)
                && Objects.equals(mPropertyMap, otherDocument.mPropertyMap);
    }

    @Override
    public int hashCode() {
        if (mHashCode == null) {
            mHashCode = Objects.hash(
                    mNamespace,
                    mId,
                    mSchemaType,
                    mTtlMillis,
                    mScore,
                    mCreationTimestampMillis,
                    Arrays.hashCode(mProperties),
                    mPropertyMap.hashCode());
        }
        return mHashCode;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        GenericDocumentParcelCreator.writeToParcel(this, dest, flags);
    }

    /** The builder class for {@link GenericDocumentParcel}. */
    public static final class Builder {
        private String mNamespace;
        private String mId;
        private String mSchemaType;
        private long mCreationTimestampMillis;
        private long mTtlMillis;
        private int mScore;
        private Map<String, PropertyParcel> mPropertyMap;
        private boolean mBuilt = false;

        /**
         * Creates a new {@link GenericDocument.Builder}.
         *
         * <p>Document IDs are unique within a namespace.
         *
         * <p>The number of namespaces per app should be kept small for efficiency reasons.
         */
        public Builder(@NonNull String namespace, @NonNull String id, @NonNull String schemaType) {
            mNamespace = Objects.requireNonNull(namespace);
            mId = Objects.requireNonNull(id);
            mSchemaType = Objects.requireNonNull(schemaType);
            mCreationTimestampMillis = INVALID_CREATION_TIMESTAMP_MILLIS;
            mTtlMillis = DEFAULT_TTL_MILLIS;
            mScore = DEFAULT_SCORE;
            mPropertyMap = new ArrayMap<>();
        }

        /**
         * Creates a new {@link GenericDocumentParcel.Builder} from the given
         * {@link GenericDocumentParcel}.
         */
        @VisibleForTesting
        public Builder(@NonNull GenericDocumentParcel documentSafeParcel) {
            Objects.requireNonNull(documentSafeParcel);

            mNamespace = documentSafeParcel.mNamespace;
            mId = documentSafeParcel.mId;
            mSchemaType = documentSafeParcel.mSchemaType;
            mCreationTimestampMillis = documentSafeParcel.mCreationTimestampMillis;
            mTtlMillis = documentSafeParcel.mTtlMillis;
            mScore = documentSafeParcel.mScore;

            // Create a shallow copy of the map so we won't change the original one.
            Map<String, PropertyParcel> propertyMap = documentSafeParcel.mPropertyMap;
            mPropertyMap = new ArrayMap<>(propertyMap.size());
            for (PropertyParcel value : propertyMap.values()) {
                mPropertyMap.put(value.getPropertyName(), value);
            }
        }

        /**
         * Sets the app-defined namespace this document resides in, changing the value provided in
         * the constructor. No special values are reserved or understood by the infrastructure.
         *
         * <p>Document IDs are unique within a namespace.
         *
         * <p>The number of namespaces per app should be kept small for efficiency reasons.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setNamespace(@NonNull String namespace) {
            Objects.requireNonNull(namespace);
            resetIfBuilt();
            mNamespace = namespace;
            return this;
        }

        /**
         * Sets the ID of this document, changing the value provided in the constructor. No special
         * values are reserved or understood by the infrastructure.
         *
         * <p>Document IDs are unique within a namespace.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setId(@NonNull String id) {
            Objects.requireNonNull(id);
            resetIfBuilt();
            mId = id;
            return this;
        }

        /**
         * Sets the schema type of this document, changing the value provided in the constructor.
         *
         * <p>To successfully index a document, the schema type must match the name of an {@link
         * AppSearchSchema} object previously provided to {@link AppSearchSession#setSchema}.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setSchemaType(@NonNull String schemaType) {
            Objects.requireNonNull(schemaType);
            resetIfBuilt();
            mSchemaType = schemaType;
            return this;
        }

        /** Sets the score of the parent {@link GenericDocument}. */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setScore(int score) {
            resetIfBuilt();
            mScore = score;
            return this;
        }

        /**
         * Sets the creation timestamp of the {@link GenericDocument}, in milliseconds.
         *
         * <p>This should be set using a value obtained from the {@link System#currentTimeMillis}
         * time base.
         *
         * <p>If this method is not called, this will be set to the time the object is built.
         *
         * @param creationTimestampMillis a creation timestamp in milliseconds.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setCreationTimestampMillis(
                /*@exportToFramework:CurrentTimeMillisLong*/ long creationTimestampMillis) {
            resetIfBuilt();
            mCreationTimestampMillis = creationTimestampMillis;
            return this;
        }

        /**
         * Sets the TTL (time-to-live) of the {@link GenericDocument}, in milliseconds.
         *
         * <p>The TTL is measured against {@link #getCreationTimestampMillis}. At the timestamp of
         * {@code creationTimestampMillis + ttlMillis}, measured in the {@link
         * System#currentTimeMillis} time base, the document will be auto-deleted.
         *
         * <p>The default value is 0, which means the document is permanent and won't be
         * auto-deleted until the app is uninstalled or {@link AppSearchSession#remove} is called.
         *
         * @param ttlMillis a non-negative duration in milliseconds.
         * @throws IllegalArgumentException if ttlMillis is negative.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder setTtlMillis(long ttlMillis) {
            if (ttlMillis < 0) {
                throw new IllegalArgumentException("Document ttlMillis cannot be negative.");
            }
            resetIfBuilt();
            mTtlMillis = ttlMillis;
            return this;
        }

        /**
         * Clears the value for the property with the given name.
         *
         * <p>Note that this method does not support property paths.
         *
         * @param name The name of the property to clear.
         */
        @CanIgnoreReturnValue
        @NonNull
        public Builder clearProperty(@NonNull String name) {
            Objects.requireNonNull(name);
            resetIfBuilt();
            mPropertyMap.remove(name);
            return this;
        }

        /** puts an array of {@link String} in property map. */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name, @NonNull String[] values)
                throws IllegalArgumentException {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setStringValues(values).build());
            return this;
        }

        /** puts an array of boolean in property map. */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name, @NonNull boolean[] values) {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setBooleanValues(values).build());
            return this;
        }

        /** puts an array of double in property map. */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name, @NonNull double[] values) {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setDoubleValues(values).build());
            return this;
        }

        /** puts an array of long in property map. */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name, @NonNull long[] values) {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setLongValues(values).build());
            return this;
        }

        /**
         * Converts and saves a byte[][] into {@link #mProperties}.
         */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name, @NonNull byte[][] values) {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setBytesValues(values).build());
            return this;
        }

        /** puts an array of {@link GenericDocumentParcel} in property map. */
        @NonNull
        public Builder putInPropertyMap(@NonNull String name,
                @NonNull GenericDocumentParcel[] values) {
            mPropertyMap.put(name,
                    new PropertyParcel.Builder(name).setDocumentValues(values).build());
            return this;
        }

        /** Builds the {@link GenericDocument} object. */
        @NonNull
        public GenericDocumentParcel build() {
            mBuilt = true;
            // Set current timestamp for creation timestamp by default.
            if (mCreationTimestampMillis == INVALID_CREATION_TIMESTAMP_MILLIS) {
                mCreationTimestampMillis = System.currentTimeMillis();
            }
            return new GenericDocumentParcel(
                    mNamespace,
                    mId,
                    mSchemaType,
                    mCreationTimestampMillis,
                    mTtlMillis,
                    mScore,
                    mPropertyMap.values().toArray(new PropertyParcel[0]));
        }

        void resetIfBuilt() {
            if (mBuilt) {
                Map<String, PropertyParcel> propertyMap = mPropertyMap;
                mPropertyMap = new ArrayMap<>(propertyMap.size());
                for (PropertyParcel value : propertyMap.values()) {
                    // PropertyParcel is not deep copied since it is not mutable.
                    mPropertyMap.put(value.getPropertyName(), value);
                }
                mBuilt = false;
            }
        }
    }
}
