/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.appsearch.app;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appsearch.flags.FlaggedApi;
import androidx.appsearch.flags.Flags;
import androidx.appsearch.safeparcel.AbstractSafeParcelable;
import androidx.appsearch.safeparcel.SafeParcelable;
import androidx.appsearch.safeparcel.stub.StubCreators.AppSearchBlobHandleCreator;
import androidx.appsearch.util.IndentingStringBuilder;
import androidx.core.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

/**
 * An identifier to represent a Blob in AppSearch.
 */
// TODO(b/273591938) improve the java doc about how AppSearchBlobHandle will be used together with
//  GenericDocument as a property when we support set blob property in GenericDocument
@FlaggedApi(Flags.FLAG_ENABLE_BLOB_STORE)
@SuppressWarnings("HiddenSuperclass")
@SafeParcelable.Class(creator = "AppSearchBlobHandleCreator")
@ExperimentalAppSearchApi
public final class AppSearchBlobHandle extends AbstractSafeParcelable {
    /** The length of the SHA-256 digest in bytes. SHA-256 produces a 256-bit (32-byte) digest. */
    private static final int SHA_256_DIGEST_BYTE_LENGTH = 32;

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static final Parcelable.Creator<AppSearchBlobHandle> CREATOR =
            new AppSearchBlobHandleCreator();
    @NonNull
    @Field(id = 1, getter = "getSha256Digest")
    private final byte[] mSha256Digest;

    @NonNull
    @Field(id = 2, getter = "getPackageName")
    private final String mPackageName;

    @NonNull
    @Field(id = 3, getter = "getDatabaseName")
    private final String mDatabaseName;

    @NonNull
    @Field(id = 4, getter = "getNamespace")
    private final String mNamespace;

    @Nullable
    private Integer mHashCode;

    /**
     * Build an {@link AppSearchBlobHandle}.
     * @exportToFramework:hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Constructor
    AppSearchBlobHandle(
            @Param(id = 1) @NonNull byte[] sha256Digest,
            @Param(id = 2) @NonNull String packageName,
            @Param(id = 3) @NonNull String databaseName,
            @Param(id = 4) @NonNull String namespace) {
        mSha256Digest = Preconditions.checkNotNull(sha256Digest);
        Preconditions.checkState(sha256Digest.length == SHA_256_DIGEST_BYTE_LENGTH,
                "The input digest isn't a sha-256 digest.");
        mPackageName = Preconditions.checkNotNull(packageName);
        mDatabaseName = Preconditions.checkNotNull(databaseName);
        mNamespace = Preconditions.checkNotNull(namespace);
    }

    /**
     * Returns the SHA-256 hash of the blob that this object is representing.
     *
     * <p> For two objects of {@link AppSearchBlobHandle} to be considered equal, the
     * {@code packageName}, {@code database}, {@code namespace} and {@code digest} must be equal.
     */
    @NonNull
    public byte[] getSha256Digest() {
        return mSha256Digest;
    }

    /**
     * Returns the package name indicating the owner app of the blob that this object is
     * representing.
     *
     * <p> For two objects of {@link AppSearchBlobHandle} to be considered equal, the
     * {@code packageName}, {@code database}, {@code namespace} and {@code digest} must be equal.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Returns the name of database stored the blob that this object is representing.
     *
     * <p> For two objects of {@link AppSearchBlobHandle} to be considered equal, the
     * {@code packageName}, {@code database}, {@code namespace} and {@code digest} must be equal.
     */
    @NonNull
    public String getDatabaseName() {
        return mDatabaseName;
    }

    /**
     * Returns the app-defined namespace this blob resides in.
     *
     * <p> For two objects of {@link AppSearchBlobHandle} to be considered equal, the
     * {@code packageName}, {@code database}, {@code namespace} and {@code digest} must be equal.
     */
    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSearchBlobHandle)) return false;

        AppSearchBlobHandle that = (AppSearchBlobHandle) o;
        if (!Arrays.equals(mSha256Digest, that.mSha256Digest)) return false;
        return mPackageName.equals(that.mPackageName) && mDatabaseName.equals(that.mDatabaseName)
                && mNamespace.equals(that.mNamespace);
    }

    @Override
    public int hashCode() {
        if (mHashCode == null) {
            mHashCode = Objects.hash(
                    Arrays.hashCode(mSha256Digest), mPackageName, mDatabaseName, mNamespace);
        }
        return mHashCode;
    }

    @NonNull
    @Override
    public String toString() {
        IndentingStringBuilder builder = new IndentingStringBuilder();
        builder.append("{\n");
        builder.increaseIndentLevel();
        builder.append("packageName: \"").append(mPackageName).append("\",\n");
        builder.append("databaseName: \"").append(mDatabaseName).append("\",\n");
        builder.append("namespace: \"").append(mNamespace).append("\",\n");
        builder.append("digest: \"");
        for (byte b : mSha256Digest) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        builder.append("\",\n").decreaseIndentLevel();
        builder.append("}");

        return builder.toString();
    }

    /**
     * Create a new AppSearch blob identifier with given digest, package, database and namespace.
     *
     * <p> The package name and database name indicated where this blob will be stored. To write,
     * commit or read this blob via {@link AppSearchSession}, it must match the package name and
     * database name of {@link AppSearchSession}.
     *
     * <p> For two objects of {@link AppSearchBlobHandle} to be considered equal, the
     * {@code packageName}, {@code database}, {@code namespace} and {@code digest} must be equal.
     *
     * @param digest        The SHA-256 hash of the blob this is representing.
     * @param packageName   The package name of the owner of this Blob.
     * @param databaseName  The database name of this blob to stored into.
     * @param namespace     The namespace of this blob resides in.
     *
     * @return a new instance of {@link AppSearchBlobHandle} object.
     */
    @NonNull
    public static AppSearchBlobHandle createWithSha256(@NonNull byte[] digest,
            @NonNull String packageName, @NonNull String databaseName, @NonNull String namespace) {
        Preconditions.checkNotNull(digest);
        Preconditions.checkArgument(digest.length == SHA_256_DIGEST_BYTE_LENGTH,
                "The digest is not a SHA-256 digest");
        Preconditions.checkNotNull(packageName);
        Preconditions.checkNotNull(databaseName);
        Preconditions.checkNotNull(namespace);
        return new AppSearchBlobHandle(digest, packageName, databaseName, namespace);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        AppSearchBlobHandleCreator.writeToParcel(this, dest, flags);
    }
}
