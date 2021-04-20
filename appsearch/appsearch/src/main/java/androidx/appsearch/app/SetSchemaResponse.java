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

package androidx.appsearch.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.collection.ArraySet;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** The response class of {@link AppSearchSession#setSchema} */
public class SetSchemaResponse {

    private static final String DELETED_TYPES_FIELD = "deletedTypes";
    private static final String INCOMPATIBLE_TYPES_FIELD = "incompatibleTypes";
    private static final String MIGRATED_TYPES_FIELD = "migratedTypes";

    private final Bundle mBundle;
    /**
     * The migrationFailures won't be saved in the bundle. Since:
     * <ul>
     *     <li>{@link MigrationFailure} is generated in {@link AppSearchSession} which will be
     *         the SDK side in platform. We don't need to pass it from service side via binder.
     *     <li>Translate multiple {@link MigrationFailure}s to bundles in {@link Builder} and then
     *         back in constructor will be a huge waste.
     * </ul>
     */
    private final List<MigrationFailure> mMigrationFailures;

    /** Cache of the inflated deleted schema types. Comes from inflating mBundles at first use. */
    @Nullable
    private Set<String> mDeletedTypes;

    /** Cache of the inflated migrated schema types. Comes from inflating mBundles at first use. */
    @Nullable
    private Set<String> mMigratedTypes;

    /**
     * Cache of the inflated incompatible schema types. Comes from inflating mBundles at first use.
     */
    @Nullable
    private Set<String> mIncompatibleTypes;

    SetSchemaResponse(@NonNull Bundle bundle, @NonNull List<MigrationFailure> migrationFailures) {
        mBundle = Preconditions.checkNotNull(bundle);
        mMigrationFailures = Preconditions.checkNotNull(migrationFailures);
    }

    SetSchemaResponse(@NonNull Bundle bundle) {
        this(bundle, /*migrationFailures=*/ Collections.emptyList());
    }

    /**
     * Returns the {@link Bundle} populated by this builder.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Bundle getBundle() {
        return mBundle;
    }

    /**
     * Returns a {@link List} of all failed {@link MigrationFailure}.
     *
     * <p>A {@link MigrationFailure} will be generated if the system trying to save a post-migrated
     * {@link GenericDocument} but fail.
     *
     * <p>{@link MigrationFailure} contains the namespace, id and schemaType of the post-migrated
     * {@link GenericDocument} and the error reason. Mostly it will be mismatch the schema it
     * migrated to.
     */
    @NonNull
    public List<MigrationFailure> getMigrationFailures() {
        return Collections.unmodifiableList(mMigrationFailures);
    }

    /**
     * Returns a {@link Set} of schema type that were deleted by the
     * {@link AppSearchSession#setSchema} call.
     */
    @NonNull
    public Set<String> getDeletedTypes() {
        if (mDeletedTypes == null) {
            mDeletedTypes = new ArraySet<>(
                    Preconditions.checkNotNull(mBundle.getStringArrayList(DELETED_TYPES_FIELD)));
        }
        return Collections.unmodifiableSet(mDeletedTypes);
    }

    /**
     * Returns a {@link Set} of schema type that were migrated by the
     * {@link AppSearchSession#setSchema} call.
     */
    @NonNull
    public Set<String> getMigratedTypes() {
        if (mMigratedTypes == null) {
            mMigratedTypes = new ArraySet<>(
                    Preconditions.checkNotNull(mBundle.getStringArrayList(MIGRATED_TYPES_FIELD)));
        }
        return Collections.unmodifiableSet(mMigratedTypes);
    }

    /**
     * Returns a {@link Set} of schema type whose new definitions set in the
     * {@link AppSearchSession#setSchema} call were incompatible with the pre-existing schema.
     *
     * <p>If a {@link Migrator} is provided for this type and the migration is success triggered.
     * The type will also appear in {@link #getMigratedTypes()}.
     *
     * @see AppSearchSession#setSchema
     * @see SetSchemaRequest.Builder#setForceOverride
     */
    @NonNull
    public Set<String> getIncompatibleTypes() {
        if (mIncompatibleTypes == null) {
            mIncompatibleTypes = new ArraySet<>(
                    Preconditions.checkNotNull(
                            mBundle.getStringArrayList(INCOMPATIBLE_TYPES_FIELD)));
        }
        return Collections.unmodifiableSet(mIncompatibleTypes);
    }

    /**
     * Translates the {@link SetSchemaResponse}'s bundle to {@link Builder}.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    // TODO(b/179302942) change to Builder(mBundle) powered by mBundle.deepCopy
    public Builder toBuilder() {
        return new Builder()
                .addDeletedTypes(getDeletedTypes())
                .addIncompatibleTypes(getIncompatibleTypes())
                .addMigratedTypes(getMigratedTypes())
                .addMigrationFailures(mMigrationFailures);
    }

    /** Builder for {@link SetSchemaResponse} objects. */
    public static final class Builder {
        private final ArrayList<MigrationFailure> mMigrationFailures = new ArrayList<>();
        private final ArrayList<String> mDeletedTypes = new ArrayList<>();
        private final ArrayList<String> mMigratedTypes = new ArrayList<>();
        private final ArrayList<String> mIncompatibleTypes = new ArrayList<>();
        private boolean mBuilt = false;

        /**  Adds {@link MigrationFailure}s to the list of migration failures. */
        @NonNull
        public Builder addMigrationFailures(
                @NonNull Collection<MigrationFailure> migrationFailures) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mMigrationFailures.addAll(Preconditions.checkNotNull(migrationFailures));
            return this;
        }

        /**  Adds a {@link MigrationFailure} to the list of migration failures. */
        @NonNull
        public Builder addMigrationFailure(@NonNull MigrationFailure migrationFailure) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mMigrationFailures.add(Preconditions.checkNotNull(migrationFailure));
            return this;
        }

        /**  Adds deletedTypes to the list of deleted schema types. */
        @NonNull
        public Builder addDeletedTypes(@NonNull Collection<String> deletedTypes) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mDeletedTypes.addAll(Preconditions.checkNotNull(deletedTypes));
            return this;
        }

        /**  Adds one deletedType to the list of deleted schema types. */
        @NonNull
        public Builder addDeletedType(@NonNull String deletedType) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mDeletedTypes.add(Preconditions.checkNotNull(deletedType));
            return this;
        }

        /**  Adds incompatibleTypes to the list of incompatible schema types. */
        @NonNull
        public Builder addIncompatibleTypes(@NonNull Collection<String> incompatibleTypes) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mIncompatibleTypes.addAll(Preconditions.checkNotNull(incompatibleTypes));
            return this;
        }

        /**  Adds one incompatibleType to the list of incompatible schema types. */
        @NonNull
        public Builder addIncompatibleType(@NonNull String incompatibleType) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mIncompatibleTypes.add(Preconditions.checkNotNull(incompatibleType));
            return this;
        }

        /**  Adds migratedTypes to the list of migrated schema types. */
        @NonNull
        public Builder addMigratedTypes(@NonNull Collection<String> migratedTypes) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mMigratedTypes.addAll(Preconditions.checkNotNull(migratedTypes));
            return this;
        }

        /**  Adds one migratedType to the list of migrated schema types. */
        @NonNull
        public Builder addMigratedType(@NonNull String migratedType) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mMigratedTypes.add(Preconditions.checkNotNull(migratedType));
            return this;
        }

        /** Builds a {@link SetSchemaResponse} object. */
        @NonNull
        public SetSchemaResponse build() {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            Bundle bundle = new Bundle();
            bundle.putStringArrayList(INCOMPATIBLE_TYPES_FIELD, mIncompatibleTypes);
            bundle.putStringArrayList(DELETED_TYPES_FIELD, mDeletedTypes);
            bundle.putStringArrayList(MIGRATED_TYPES_FIELD, mMigratedTypes);
            mBuilt = true;
            // Avoid converting the potential thousands of MigrationFailures to Pracelable and
            // back just for put in bundle. In platform, we should set MigrationFailures in
            // AppSearchSession after we pass SetSchemaResponse via binder.
            return new SetSchemaResponse(bundle, mMigrationFailures);
        }
    }

    /**
     * The class represents a post-migrated {@link GenericDocument} that failed to be saved by
     * {@link AppSearchSession#setSchema}.
     */
    public static class MigrationFailure {
        private static final String SCHEMA_TYPE_FIELD = "schemaType";
        private static final String NAMESPACE_FIELD = "namespace";
        private static final String DOCUMENT_ID_FIELD = "id";
        private static final String ERROR_MESSAGE_FIELD = "errorMessage";
        private static final String RESULT_CODE_FIELD = "resultCode";

        private final Bundle mBundle;

        /**
         * Constructs a new {@link MigrationFailure}.
         *
         * @param namespace    The namespace of the document which failed to be migrated.
         * @param documentId   The id of the document which failed to be migrated.
         * @param schemaType   The type of the document which failed to be migrated.
         * @param failedResult The reason why the document failed to be indexed.
         * @throws IllegalArgumentException if the provided {@code failedResult} was not a failure.
         */
        public MigrationFailure(
                @NonNull String namespace,
                @NonNull String documentId,
                @NonNull String schemaType,
                @NonNull AppSearchResult<?> failedResult) {
            mBundle = new Bundle();
            mBundle.putString(NAMESPACE_FIELD, Preconditions.checkNotNull(namespace));
            mBundle.putString(DOCUMENT_ID_FIELD, Preconditions.checkNotNull(documentId));
            mBundle.putString(SCHEMA_TYPE_FIELD, Preconditions.checkNotNull(schemaType));

            Preconditions.checkNotNull(failedResult);
            Preconditions.checkArgument(
                    !failedResult.isSuccess(), "failedResult was actually successful");
            mBundle.putString(ERROR_MESSAGE_FIELD, failedResult.getErrorMessage());
            mBundle.putInt(RESULT_CODE_FIELD, failedResult.getResultCode());
        }

        MigrationFailure(@NonNull Bundle bundle) {
            mBundle = Preconditions.checkNotNull(bundle);
        }

        /**
         * Returns the Bundle of the {@link MigrationFailure}.
         *
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Bundle getBundle() {
            return mBundle;
        }

        /** Returns the namespace of the {@link GenericDocument} that failed to be migrated. */
        @NonNull
        public String getNamespace() {
            return mBundle.getString(NAMESPACE_FIELD, /*defaultValue=*/"");
        }

        /** Returns the id of the {@link GenericDocument} that failed to be migrated. */
        @NonNull
        public String getDocumentId() {
            return mBundle.getString(DOCUMENT_ID_FIELD, /*defaultValue=*/"");
        }

        /** Returns the schema type of the {@link GenericDocument} that failed to be migrated. */
        @NonNull
        public String getSchemaType() {
            return mBundle.getString(SCHEMA_TYPE_FIELD, /*defaultValue=*/"");
        }

        /**
         * Returns the {@link AppSearchResult} that indicates why the
         * post-migration {@link GenericDocument} failed to be indexed.
         */
        @NonNull
        public AppSearchResult<Void> getAppSearchResult() {
            return AppSearchResult.newFailedResult(mBundle.getInt(RESULT_CODE_FIELD),
                    mBundle.getString(ERROR_MESSAGE_FIELD, /*defaultValue=*/""));
        }
    }
}
