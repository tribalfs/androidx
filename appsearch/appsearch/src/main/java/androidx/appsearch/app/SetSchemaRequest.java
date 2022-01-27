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

package androidx.appsearch.app;

import android.annotation.SuppressLint;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresFeature;
import androidx.annotation.RestrictTo;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.collection.ArrayMap;
import androidx.collection.ArraySet;
import androidx.core.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates a request to update the schema of an {@link AppSearchSession} database.
 *
 * <p>The schema is composed of a collection of {@link AppSearchSchema} objects, each of which
 * defines a unique type of data.
 *
 * <p>The first call to SetSchemaRequest will set the provided schema and store it within the
 * {@link AppSearchSession} database.
 *
 * <p>Subsequent calls will compare the provided schema to the previously saved schema, to
 * determine how to treat existing documents.
 *
 * <p>The following types of schema modifications are always safe and are made without deleting any
 * existing documents:
 * <ul>
 *     <li>Addition of new {@link AppSearchSchema} types
 *     <li>Addition of new properties to an existing {@link AppSearchSchema} type
 *     <li>Changing the cardinality of a property to be less restrictive
 * </ul>
 *
 * <p>The following types of schema changes are not backwards compatible:
 * <ul>
 *     <li>Removal of an existing {@link AppSearchSchema} type
 *     <li>Removal of a property from an existing {@link AppSearchSchema} type
 *     <li>Changing the data type of an existing property
 *     <li>Changing the cardinality of a property to be more restrictive
 * </ul>
 *
 * <p>Providing a schema with incompatible changes, will throw an
 * {@link androidx.appsearch.exceptions.AppSearchException}, with a message describing the
 * incompatibility. As a result, the previously set schema will remain unchanged.
 *
 * <p>Backward incompatible changes can be made by :
 * <ul>
 *     <li>setting {@link SetSchemaRequest.Builder#setForceOverride} method to {@code true}.
 *         This deletes all documents that are incompatible with the new schema. The new schema is
 *         then saved and persisted to disk.
 *     <li>Add a {@link Migrator} for each incompatible type and make no deletion. The migrator
 *         will migrate documents from it's old schema version to the new version. Migrated types
 *         will be set into both {@link SetSchemaResponse#getIncompatibleTypes()} and
 *         {@link SetSchemaResponse#getMigratedTypes()}. See the migration section below.
 * </ul>
 * @see AppSearchSession#setSchema
 * @see Migrator
 */
public final class SetSchemaRequest {

    /**
     * List of Android Roles are supported in
     * {@link SetSchemaRequest.Builder#addAllowedRoleForSchemaTypeVisibility}
     *
     * @see android.app.role.RoleManager
     * @hide
     */
    @IntDef(value = {
            ROLE_HOME,
            ROLE_ASSISTANT,
    })
    @Retention(RetentionPolicy.SOURCE)
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @interface AppSearchSupportedRole {}

    /**
     * The {@link android.app.role.RoleManager#ROLE_HOME} AppSearch supported in
     * {@link SetSchemaRequest.Builder#addAllowedRoleForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int ROLE_HOME = 1;

    /**
     * The {@link android.app.role.RoleManager#ROLE_ASSISTANT} AppSearch supported in
     * {@link SetSchemaRequest.Builder#addAllowedRoleForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int ROLE_ASSISTANT = 2;

    /**
     * List of Android Permission are supported in
     * {@link SetSchemaRequest.Builder#setRequiredPermissionsForSchemaTypeVisibility}
     *
     * @see android.Manifest.permission
     * @hide
     */
    @IntDef(value = {
            READ_SMS,
            READ_CALENDAR,
            READ_CONTACTS,
            READ_EXTERNAL_STORAGE,
    })
    @Retention(RetentionPolicy.SOURCE)
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public @interface AppSearchSupportedPermission {}

    /**
     * The {@link android.Manifest.permission#READ_SMS} AppSearch supported in
     * {@link SetSchemaRequest.Builder#setRequiredPermissionsForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int READ_SMS = 1;

    /**
     * The {@link android.Manifest.permission#READ_CALENDAR} AppSearch supported in
     * {@link SetSchemaRequest.Builder#setRequiredPermissionsForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int READ_CALENDAR = 2;

    /**
     * The {@link android.Manifest.permission#READ_CONTACTS} AppSearch supported in
     * {@link SetSchemaRequest.Builder#setRequiredPermissionsForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int READ_CONTACTS = 3;

    /**
     * The {@link android.Manifest.permission#READ_EXTERNAL_STORAGE} AppSearch supported in
     * {@link SetSchemaRequest.Builder#setRequiredPermissionsForSchemaTypeVisibility}
     */
    // @exportToFramework:startStrip()
    @RequiresFeature(
            enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
            name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
    // @exportToFramework:endStrip()
    public static final int READ_EXTERNAL_STORAGE = 4;

    private final Set<AppSearchSchema> mSchemas;
    private final Set<String> mSchemasNotDisplayedBySystem;
    private final Map<String, Set<PackageIdentifier>> mSchemasVisibleToPackages;
    private final Map<String, Set<Integer>> mSchemasVisibleToRoles;
    private final Map<String, Set<Integer>> mSchemasVisibleToPermissions;
    private final Map<String, Migrator> mMigrators;
    private final boolean mForceOverride;
    private final int mVersion;

    SetSchemaRequest(@NonNull Set<AppSearchSchema> schemas,
            @NonNull Set<String> schemasNotDisplayedBySystem,
            @NonNull Map<String, Set<PackageIdentifier>> schemasVisibleToPackages,
            @NonNull Map<String, Set<Integer>> schemasVisibleToRoles,
            @NonNull Map<String, Set<Integer>> schemasVisibleToPermissions,
            @NonNull Map<String, Migrator> migrators,
            boolean forceOverride,
            int version) {
        mSchemas = Preconditions.checkNotNull(schemas);
        mSchemasNotDisplayedBySystem = Preconditions.checkNotNull(schemasNotDisplayedBySystem);
        mSchemasVisibleToPackages = Preconditions.checkNotNull(schemasVisibleToPackages);
        mSchemasVisibleToRoles = Preconditions.checkNotNull(schemasVisibleToRoles);
        mSchemasVisibleToPermissions = Preconditions.checkNotNull(schemasVisibleToPermissions);
        mMigrators = Preconditions.checkNotNull(migrators);
        mForceOverride = forceOverride;
        mVersion = version;
    }

    /** Returns the {@link AppSearchSchema} types that are part of this request. */
    @NonNull
    public Set<AppSearchSchema> getSchemas() {
        return Collections.unmodifiableSet(mSchemas);
    }

    /**
     * Returns all the schema types that are opted out of being displayed and visible on any
     * system UI surface.
     */
    @NonNull
    public Set<String> getSchemasNotDisplayedBySystem() {
        return Collections.unmodifiableSet(mSchemasNotDisplayedBySystem);
    }

    /**
     * Returns a mapping of schema types to the set of packages that have access
     * to that schema type.
     *
     * <p>It’s inefficient to call this method repeatedly.
     */
    @NonNull
    public Map<String, Set<PackageIdentifier>> getSchemasVisibleToPackages() {
        Map<String, Set<PackageIdentifier>> copy = new ArrayMap<>();
        for (Map.Entry<String, Set<PackageIdentifier>> entry :
                mSchemasVisibleToPackages.entrySet()) {
            copy.put(entry.getKey(), new ArraySet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Returns the map of schema types to the set of Roles that have access to that schema type.
     *
     * <p>The querier will have access to the schema type if they hold ANY of allowed Roles.
     *
     * <p>It’s inefficient to call this method repeatedly.
     *
     * @return The map contains schema type and all allowed role can access it. The supported
     *         Role are {@link #ROLE_HOME} and {@link #ROLE_ASSISTANT}
     */
    @NonNull
    public Map<String, Set<Integer>> getAllowedRolesForSchemaTypeVisibility() {
        Map<String, Set<Integer>> copy = new ArrayMap<>();
        for (Map.Entry<String, Set<Integer>> entry : mSchemasVisibleToRoles.entrySet()) {
            copy.put(entry.getKey(), new ArraySet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Returns the map of schema types to the set of {@link android.Manifest.permission} that
     * querier must hold to have access to that schema type.
     *
     * <p> To get {@link GenericDocument} of a schema type, the call must hold ALL of the
     * required permissions for that schema type.
     *
     * <p>It’s inefficient to call this method repeatedly.
     *
     * @return The map contains schema type and all required permission for querier to access it.
     *         The supported Permission are {@link #READ_SMS}, {@link #READ_CALENDAR},
     *         {@link #READ_CONTACTS}, {@link #READ_EXTERNAL_STORAGE}.
     */
    @NonNull
    public Map<String, Set<Integer>> getRequiredPermissionsForSchemaTypeVisibility() {
        Map<String, Set<Integer>> copy = new ArrayMap<>();
        for (Map.Entry<String, Set<Integer>> entry : mSchemasVisibleToPermissions.entrySet()) {
            copy.put(entry.getKey(), new ArraySet<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Returns the map of {@link Migrator}, the key will be the schema type of the
     * {@link Migrator} associated with.
     */
    @NonNull
    public Map<String, Migrator> getMigrators() {
        return Collections.unmodifiableMap(mMigrators);
    }

    /**
     * Returns a mapping of {@link AppSearchSchema} types to the set of packages that have access
     * to that schema type.
     *
     * <p>A more efficient version of {@link #getSchemasVisibleToPackages}, but it returns a
     * modifiable map. This is not meant to be unhidden and should only be used by internal
     * classes.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public Map<String, Set<PackageIdentifier>> getSchemasVisibleToPackagesInternal() {
        return mSchemasVisibleToPackages;
    }

    /** Returns whether this request will force the schema to be overridden. */
    public boolean isForceOverride() {
        return mForceOverride;
    }

    /** Returns the database overall schema version. */
    @IntRange(from = 1)
    public int getVersion() {
        return mVersion;
    }

    /** Builder for {@link SetSchemaRequest} objects. */
    public static final class Builder {
        private static final int DEFAULT_VERSION = 1;
        private ArraySet<AppSearchSchema> mSchemas = new ArraySet<>();
        private ArraySet<String> mSchemasNotDisplayedBySystem = new ArraySet<>();
        private ArrayMap<String, Set<PackageIdentifier>> mSchemasVisibleToPackages =
                new ArrayMap<>();
        private ArrayMap<String, Set<Integer>> mSchemasVisibleToRoles = new ArrayMap<>();
        private ArrayMap<String, Set<Integer>> mSchemasVisibleToPermissions = new ArrayMap<>();
        private ArrayMap<String, Migrator> mMigrators = new ArrayMap<>();
        private boolean mForceOverride = false;
        private int mVersion = DEFAULT_VERSION;
        private boolean mBuilt = false;

        /**
         * Adds one or more {@link AppSearchSchema} types to the schema.
         *
         * <p>An {@link AppSearchSchema} object represents one type of structured data.
         *
         * <p>Any documents of these types will be displayed on system UI surfaces by default.
         */
        @NonNull
        public Builder addSchemas(@NonNull AppSearchSchema... schemas) {
            Preconditions.checkNotNull(schemas);
            resetIfBuilt();
            return addSchemas(Arrays.asList(schemas));
        }

        /**
         * Adds a collection of {@link AppSearchSchema} objects to the schema.
         *
         * <p>An {@link AppSearchSchema} object represents one type of structured data.
         */
        @NonNull
        public Builder addSchemas(@NonNull Collection<AppSearchSchema> schemas) {
            Preconditions.checkNotNull(schemas);
            resetIfBuilt();
            mSchemas.addAll(schemas);
            return this;
        }

// @exportToFramework:startStrip()
        /**
         * Adds one or more {@link androidx.appsearch.annotation.Document} annotated classes to the
         * schema.
         *
         * @param documentClasses classes annotated with
         *                        {@link androidx.appsearch.annotation.Document}.
         * @throws AppSearchException if {@code androidx.appsearch.compiler.AppSearchCompiler}
         *                            has not generated a schema for the given document classes.
         */
        @SuppressLint("MissingGetterMatchingBuilder")  // Merged list available from getSchemas()
        @NonNull
        public Builder addDocumentClasses(@NonNull Class<?>... documentClasses)
                throws AppSearchException {
            Preconditions.checkNotNull(documentClasses);
            resetIfBuilt();
            return addDocumentClasses(Arrays.asList(documentClasses));
        }

        /**
         * Adds a collection of {@link androidx.appsearch.annotation.Document} annotated classes to
         * the schema.
         *
         * @param documentClasses classes annotated with
         *                        {@link androidx.appsearch.annotation.Document}.
         * @throws AppSearchException if {@code androidx.appsearch.compiler.AppSearchCompiler}
         *                            has not generated a schema for the given document classes.
         */
        @SuppressLint("MissingGetterMatchingBuilder")  // Merged list available from getSchemas()
        @NonNull
        public Builder addDocumentClasses(@NonNull Collection<? extends Class<?>> documentClasses)
                throws AppSearchException {
            Preconditions.checkNotNull(documentClasses);
            resetIfBuilt();
            List<AppSearchSchema> schemas = new ArrayList<>(documentClasses.size());
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            for (Class<?> documentClass : documentClasses) {
                DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
                schemas.add(factory.getSchema());
            }
            return addSchemas(schemas);
        }
// @exportToFramework:endStrip()

        /**
         * Sets whether or not documents from the provided {@code schemaType} will be displayed
         * and visible on any system UI surface.
         *
         * <p>This setting applies to the provided {@code schemaType} only, and does not persist
         * across {@link AppSearchSession#setSchema} calls.
         *
         * <p>The default behavior, if this method is not called, is to allow types to be
         * displayed on system UI surfaces.
         *
         * @param schemaType The name of an {@link AppSearchSchema} within the same
         *                   {@link SetSchemaRequest}, which will be configured.
         * @param displayed  Whether documents of this type will be displayed on system UI surfaces.
         */
        // Merged list available from getSchemasNotDisplayedBySystem
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setSchemaTypeDisplayedBySystem(
                @NonNull String schemaType, boolean displayed) {
            Preconditions.checkNotNull(schemaType);
            resetIfBuilt();
            if (displayed) {
                mSchemasNotDisplayedBySystem.remove(schemaType);
            } else {
                mSchemasNotDisplayedBySystem.add(schemaType);
            }
            return this;
        }

        /**
         * Add an Android Role who can read documents from the provided {@code schemaType}.
         *
         * <p>The querier could read documents from the provided {@code schemaType} if they hold
         * ANY of allowed Roles.
         *
         * <p>The supported Role are {@link #ROLE_HOME} and {@link #ROLE_ASSISTANT}.
         *
         * @see android.app.role.RoleManager
         * @see android.app.role.RoleManager#ROLE_HOME
         * @see android.app.role.RoleManager#ROLE_ASSISTANT
         *
         * @param schemaType        The schema type to set visibility on.
         * @param role              The Android role who can access the {@link GenericDocument}
         *                          objects that under the given schema. The allowed values are
         *                          {@link android.app.role.RoleManager#ROLE_HOME} and
         *                          {@link android.app.role.RoleManager#ROLE_ASSISTANT}.
         * @throws IllegalArgumentException – if input unsupported role.
         */
        // Merged map available from getAllowedRolesForSchemaTypeVisibility
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        // @exportToFramework:startStrip()
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        // @exportToFramework:endStrip()
        public Builder addAllowedRoleForSchemaTypeVisibility(@NonNull String schemaType,
                @AppSearchSupportedRole int role) {
            Preconditions.checkNotNull(schemaType);
            Preconditions.checkArgumentInRange(role, ROLE_HOME, ROLE_ASSISTANT, "role");
            //TODO(b/202194495) check the input role are in the allowed list.
            resetIfBuilt();
            Set<Integer> allowedRole = mSchemasVisibleToRoles.get(schemaType);
            if (allowedRole == null) {
                allowedRole = new ArraySet<>();
                mSchemasVisibleToRoles.put(schemaType, allowedRole);
            }
            allowedRole.add(role);
            return this;
        }

        /**
         * Clears all allowed roles for the given schema type.
         *
         * @param schemaType        The schema type to set visibility on.
         */
        @NonNull
        // @exportToFramework:startStrip()
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        // @exportToFramework:endStrip()
        public Builder clearAllowedRolesForSchemaTypeVisibility(@NonNull String schemaType) {
            Preconditions.checkNotNull(schemaType);
            resetIfBuilt();
            mSchemasVisibleToRoles.remove(schemaType);
            return this;
        }

        /**
         * Sets a set of required Android {@link android.Manifest.permission} to the given schema
         * type.
         *
         * <p> To get {@link GenericDocument} of the given schema type, the call must hold ALL of
         * the required permissions.
         *
         * <p>The supported Permission are {@link #READ_SMS}, {@link #READ_CALENDAR},
         * {@link #READ_CONTACTS}, {@link #READ_EXTERNAL_STORAGE}.
         *
         * @see android.Manifest.permission#READ_SMS
         * @see android.Manifest.permission#READ_CALENDAR
         * @see android.Manifest.permission#READ_CONTACTS
         * @see android.Manifest.permission#READ_EXTERNAL_STORAGE
         * @param schemaType       The schema type to set visibility on.
         * @param permissions      A set of required Android permissions the caller need to hold
         *                         to access {@link GenericDocument} objects that under the given
         *                         schema.
         * @throws IllegalArgumentException – if input unsupported permission.
         */
        // Merged list available from getRequiredPermissionsForSchemaTypeVisibility
        @SuppressLint("MissingGetterMatchingBuilder")
        // @exportToFramework:startStrip()
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        // @exportToFramework:endStrip()
        @NonNull
        public Builder setRequiredPermissionsForSchemaTypeVisibility(@NonNull String schemaType,
                @AppSearchSupportedPermission @NonNull Set<Integer> permissions) {
            Preconditions.checkNotNull(schemaType);
            Preconditions.checkNotNull(permissions);
            for (int permission : permissions) {
                Preconditions.checkArgumentInRange(permission, READ_SMS, READ_EXTERNAL_STORAGE,
                        "permission");
            }
            resetIfBuilt();
            //TODO(b/181908338) check input permissions in our allow list
            mSchemasVisibleToPermissions.put(schemaType, permissions);
            return this;
        }

        /**
         * Sets whether or not documents from the provided {@code schemaType} can be read by the
         * specified package.
         *
         * <p>Each package is represented by a {@link PackageIdentifier}, containing a package name
         * and a byte array of type {@link android.content.pm.PackageManager#CERT_INPUT_SHA256}.
         *
         * <p>To opt into one-way data sharing with another application, the developer will need to
         * explicitly grant the other application’s package name and certificate Read access to its
         * data.
         *
         * <p>For two-way data sharing, both applications need to explicitly grant Read access to
         * one another.
         *
         * <p>By default, data sharing between applications is disabled.
         *
         * @param schemaType        The schema type to set visibility on.
         * @param visible           Whether the {@code schemaType} will be visible or not.
         * @param packageIdentifier Represents the package that will be granted visibility.
         */
        // Merged list available from getSchemasVisibleToPackages
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setSchemaTypeVisibilityForPackage(
                @NonNull String schemaType,
                boolean visible,
                @NonNull PackageIdentifier packageIdentifier) {
            Preconditions.checkNotNull(schemaType);
            Preconditions.checkNotNull(packageIdentifier);
            resetIfBuilt();

            Set<PackageIdentifier> packageIdentifiers = mSchemasVisibleToPackages.get(schemaType);
            if (visible) {
                if (packageIdentifiers == null) {
                    packageIdentifiers = new ArraySet<>();
                }
                packageIdentifiers.add(packageIdentifier);
                mSchemasVisibleToPackages.put(schemaType, packageIdentifiers);
            } else {
                if (packageIdentifiers == null) {
                    // Return early since there was nothing set to begin with.
                    return this;
                }
                packageIdentifiers.remove(packageIdentifier);
                if (packageIdentifiers.isEmpty()) {
                    // Remove the entire key so that we don't have empty sets as values.
                    mSchemasVisibleToPackages.remove(schemaType);
                }
            }

            return this;
        }

        /**
         * Sets the {@link Migrator} associated with the given SchemaType.
         *
         * <p>The {@link Migrator} migrates all {@link GenericDocument}s under given schema type
         * from the current version number stored in AppSearch to the final version set via
         * {@link #setVersion}.
         *
         * <p>A {@link Migrator} will be invoked if the current version number stored in
         * AppSearch is different from the final version set via {@link #setVersion} and
         * {@link Migrator#shouldMigrate} returns {@code true}.
         *
         * <p>The target schema type of the output {@link GenericDocument} of
         * {@link Migrator#onUpgrade} or {@link Migrator#onDowngrade} must exist in this
         * {@link SetSchemaRequest}.
         *
         * @param schemaType The schema type to set migrator on.
         * @param migrator   The migrator translates a document from its current version to the
         *                   final version set via {@link #setVersion}.
         *
         * @see SetSchemaRequest.Builder#setVersion
         * @see SetSchemaRequest.Builder#addSchemas
         * @see AppSearchSession#setSchema
         */
        @NonNull
        @SuppressLint("MissingGetterMatchingBuilder")        // Getter return plural objects.
        public Builder setMigrator(@NonNull String schemaType, @NonNull Migrator migrator) {
            Preconditions.checkNotNull(schemaType);
            Preconditions.checkNotNull(migrator);
            resetIfBuilt();
            mMigrators.put(schemaType, migrator);
            return this;
        }

        /**
         * Sets a Map of {@link Migrator}s.
         *
         * <p>The key of the map is the schema type that the {@link Migrator} value applies to.
         *
         * <p>The {@link Migrator} migrates all {@link GenericDocument}s under given schema type
         * from the current version number stored in AppSearch to the final version set via
         * {@link #setVersion}.
         *
         * <p>A {@link Migrator} will be invoked if the current version number stored in
         * AppSearch is different from the final version set via {@link #setVersion} and
         * {@link Migrator#shouldMigrate} returns {@code true}.
         *
         * <p>The target schema type of the output {@link GenericDocument} of
         * {@link Migrator#onUpgrade} or {@link Migrator#onDowngrade} must exist in this
         * {@link SetSchemaRequest}.
         *
         * @param migrators  A {@link Map} of migrators that translate a document from it's current
         *                   version to the final version set via {@link #setVersion}. The key of
         *                   the map is the schema type that the {@link Migrator} value applies to.
         *
         * @see SetSchemaRequest.Builder#setVersion
         * @see SetSchemaRequest.Builder#addSchemas
         * @see AppSearchSession#setSchema
         */
        @NonNull
        public Builder setMigrators(@NonNull Map<String, Migrator> migrators) {
            Preconditions.checkNotNull(migrators);
            resetIfBuilt();
            mMigrators.putAll(migrators);
            return this;
        }

// @exportToFramework:startStrip()

        /**
         * Sets whether or not documents from the provided
         * {@link androidx.appsearch.annotation.Document} annotated class will be displayed and
         * visible on any system UI surface.
         *
         * <p>This setting applies to the provided {@link androidx.appsearch.annotation.Document}
         * annotated class only, and does not persist across {@link AppSearchSession#setSchema}
         * calls.
         *
         * <p>The default behavior, if this method is not called, is to allow types to be
         * displayed on system UI surfaces.
         *
         * @param documentClass A class annotated with
         *                      {@link androidx.appsearch.annotation.Document}, the visibility of
         *                      which will be configured
         * @param displayed     Whether documents of this type will be displayed on system UI
         *                      surfaces.
         * @throws AppSearchException if {@code androidx.appsearch.compiler.AppSearchCompiler}
         *                            has not generated a schema for the given document class.
         */
        // Merged list available from getSchemasNotDisplayedBySystem
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setDocumentClassDisplayedBySystem(@NonNull Class<?> documentClass,
                boolean displayed) throws AppSearchException {
            Preconditions.checkNotNull(documentClass);
            resetIfBuilt();
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
            return setSchemaTypeDisplayedBySystem(factory.getSchemaName(), displayed);
        }

        /**
         * Sets whether or not documents from the provided
         * {@link androidx.appsearch.annotation.Document} annotated class can be read by the
         * specified package.
         *
         * <p>Each package is represented by a {@link PackageIdentifier}, containing a package name
         * and a byte array of type {@link android.content.pm.PackageManager#CERT_INPUT_SHA256}.
         *
         * <p>To opt into one-way data sharing with another application, the developer will need to
         * explicitly grant the other application’s package name and certificate Read access to its
         * data.
         *
         * <p>For two-way data sharing, both applications need to explicitly grant Read access to
         * one another.
         *
         * <p>By default, app data sharing between applications is disabled.
         *
         * @param documentClass     The {@link androidx.appsearch.annotation.Document} class to set
         *                          visibility on.
         * @param visible           Whether the {@code documentClass} will be visible or not.
         * @param packageIdentifier Represents the package that will be granted visibility.
         * @throws AppSearchException if {@code androidx.appsearch.compiler.AppSearchCompiler}
         *                            has not generated a schema for the given document class.
         */
        // Merged list available from getSchemasVisibleToPackages
        @SuppressLint("MissingGetterMatchingBuilder")
        @NonNull
        public Builder setDocumentClassVisibilityForPackage(@NonNull Class<?> documentClass,
                boolean visible, @NonNull PackageIdentifier packageIdentifier)
                throws AppSearchException {
            Preconditions.checkNotNull(documentClass);
            resetIfBuilt();
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
            return setSchemaTypeVisibilityForPackage(factory.getSchemaName(), visible,
                    packageIdentifier);
        }

        /**
         * Add an Android Role who can read documents from the provided
         * {@link androidx.appsearch.annotation.Document} annotated class.
         *
         * <p>The querier could read documents from the provided {@code schemaType} if they hold
         * ANY of allowed Roles.
         *
         * <p>The supported Role are {@link #ROLE_HOME} and {@link #ROLE_ASSISTANT}.
         *
         * @see android.app.role.RoleManager
         * @see android.app.role.RoleManager#ROLE_HOME
         * @see android.app.role.RoleManager#ROLE_ASSISTANT
         *
         * @param documentClass     The {@link androidx.appsearch.annotation.Document} class to set
         *                          visibility on.
         * @param role              The Android role who can access the {@link GenericDocument}
         *                          objects that under the given schema. The allowed values are
         *                          {@link android.app.role.RoleManager#ROLE_HOME} and
         *                          {@link android.app.role.RoleManager#ROLE_ASSISTANT}.
         */
        // Merged map available from getAllowedRolesForSchemaTypeVisibility
        @SuppressLint("MissingGetterMatchingBuilder")
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        @NonNull
        public Builder addAllowedRoleForDocumentClassVisibility(@NonNull Class<?> documentClass,
                @AppSearchSupportedRole int role) throws AppSearchException {
            Preconditions.checkNotNull(documentClass);
            resetIfBuilt();
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
            return addAllowedRoleForSchemaTypeVisibility(factory.getSchemaName(), role);
        }

        /**
         * Clears all allowed roles for the given
         * {@link androidx.appsearch.annotation.Document} annotated class.
         *
         * @param documentClass     The {@link androidx.appsearch.annotation.Document} class to set
         *                          visibility on.
         */
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        @NonNull
        public Builder clearAllowedRolesForDocumentClassVisibility(
                @NonNull Class<?> documentClass) throws AppSearchException {
            Preconditions.checkNotNull(documentClass);
            resetIfBuilt();
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
            return clearAllowedRolesForSchemaTypeVisibility(factory.getSchemaName());
        }

        /**
         * Sets a set of required {@link android.Manifest.permission} to the given schema type.
         *
         * <p> To get {@link GenericDocument} of the given schema type, the call must hold ALL of
         * the required permissions.
         *
         * <p>The supported Permission are {@link #READ_SMS}, {@link #READ_CALENDAR},
         * {@link #READ_CONTACTS}, {@link #READ_EXTERNAL_STORAGE}.
         *
         * @see android.Manifest.permission#READ_SMS
         * @see android.Manifest.permission#READ_CALENDAR
         * @see android.Manifest.permission#READ_CONTACTS
         * @see android.Manifest.permission#READ_EXTERNAL_STORAGE
         * @param documentClass    The {@link androidx.appsearch.annotation.Document} class to set
         *                         visibility on.
         * @param permissions      A set of required Android permissions the caller need to hold
         *                         to access {@link GenericDocument} objects that under the given
         *                         schema.
         */
        // Merged map available from getRequiredPermissionsForSchemaTypeVisibility
        @SuppressLint("MissingGetterMatchingBuilder")
        @RequiresFeature(
                enforcement = "androidx.appsearch.app.Features#isFeatureSupported",
                name = Features.ROLE_AND_PERMISSION_WITH_GET_VISIBILITY)
        @NonNull
        public Builder setRequiredPermissionsForDocumentClassVisibility(
                @NonNull Class<?> documentClass,
                @AppSearchSupportedPermission @NonNull Set<Integer> permissions)
                throws AppSearchException {
            Preconditions.checkNotNull(documentClass);
            resetIfBuilt();
            DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();
            DocumentClassFactory<?> factory = registry.getOrCreateFactory(documentClass);
            return setRequiredPermissionsForSchemaTypeVisibility(
                    factory.getSchemaName(), permissions);
        }
// @exportToFramework:endStrip()

        /**
         * Sets whether or not to override the current schema in the {@link AppSearchSession}
         * database.
         *
         * <p>Call this method whenever backward incompatible changes need to be made by setting
         * {@code forceOverride} to {@code true}. As a result, during execution of the setSchema
         * operation, all documents that are incompatible with the new schema will be deleted and
         * the new schema will be saved and persisted.
         *
         * <p>By default, this is {@code false}.
         */
        @NonNull
        public Builder setForceOverride(boolean forceOverride) {
            resetIfBuilt();
            mForceOverride = forceOverride;
            return this;
        }

        /**
         * Sets the version number of the overall {@link AppSearchSchema} in the database.
         *
         * <p>The {@link AppSearchSession} database can only ever hold documents for one version
         * at a time.
         *
         * <p>Setting a version number that is different from the version number currently stored
         * in AppSearch will result in AppSearch calling the {@link Migrator}s provided to
         * {@link AppSearchSession#setSchema} to migrate the documents already in AppSearch from
         * the previous version to the one set in this request. The version number can be
         * updated without any other changes to the set of schemas.
         *
         * <p>The version number can stay the same, increase, or decrease relative to the current
         * version number that is already stored in the {@link AppSearchSession} database.
         *
         * <p>The version of an empty database will always be 0. You cannot set version to the
         * {@link SetSchemaRequest}, if it doesn't contains any {@link AppSearchSchema}.
         *
         * @param version A positive integer representing the version of the entire set of
         *                schemas represents the version of the whole schema in the
         *                {@link AppSearchSession} database, default version is 1.
         *
         * @throws IllegalArgumentException if the version is negative.
         *
         * @see AppSearchSession#setSchema
         * @see Migrator
         * @see SetSchemaRequest.Builder#setMigrator
         */
        @NonNull
        public Builder setVersion(@IntRange(from = 1) int version) {
            Preconditions.checkArgument(version >= 1, "Version must be a positive number.");
            resetIfBuilt();
            mVersion = version;
            return this;
        }

        /**
         * Builds a new {@link SetSchemaRequest} object.
         *
         * @throws IllegalArgumentException if schema types were referenced, but the
         *                                  corresponding {@link AppSearchSchema} type was never
         *                                  added.
         */
        @NonNull
        public SetSchemaRequest build() {
            // Verify that any schema types with display or visibility settings refer to a real
            // schema.
            // Create a copy because we're going to remove from the set for verification purposes.
            Set<String> referencedSchemas = new ArraySet<>(mSchemasNotDisplayedBySystem);
            referencedSchemas.addAll(mSchemasVisibleToPackages.keySet());
            referencedSchemas.addAll(mSchemasVisibleToRoles.keySet());
            referencedSchemas.addAll(mSchemasVisibleToPermissions.keySet());

            for (AppSearchSchema schema : mSchemas) {
                referencedSchemas.remove(schema.getSchemaType());
            }
            if (!referencedSchemas.isEmpty()) {
                // We still have schema types that weren't seen in our mSchemas set. This means
                // there wasn't a corresponding AppSearchSchema.
                throw new IllegalArgumentException(
                        "Schema types " + referencedSchemas + " referenced, but were not added.");
            }
            if (mSchemas.isEmpty() && mVersion != DEFAULT_VERSION) {
                throw new IllegalArgumentException(
                        "Cannot set version to the request if schema is empty.");
            }
            mBuilt = true;
            return new SetSchemaRequest(
                    mSchemas,
                    mSchemasNotDisplayedBySystem,
                    mSchemasVisibleToPackages,
                    mSchemasVisibleToRoles,
                    mSchemasVisibleToPermissions,
                    mMigrators,
                    mForceOverride,
                    mVersion);
        }

        private void resetIfBuilt() {
            if (mBuilt) {
                ArrayMap<String, Set<PackageIdentifier>> schemasVisibleToPackages =
                        new ArrayMap<>(mSchemasVisibleToPackages.size());
                for (Map.Entry<String, Set<PackageIdentifier>> entry
                        : mSchemasVisibleToPackages.entrySet()) {
                    schemasVisibleToPackages.put(entry.getKey(), new ArraySet<>(entry.getValue()));
                }
                mSchemasVisibleToPackages = schemasVisibleToPackages;

                ArrayMap<String, Set<Integer>> schemasVisibleToRoles =
                        new ArrayMap<>(mSchemasVisibleToRoles.size());
                for (Map.Entry<String, Set<Integer>> entry : mSchemasVisibleToRoles.entrySet()) {
                    schemasVisibleToRoles.put(entry.getKey(), new ArraySet<>(entry.getValue()));
                }
                mSchemasVisibleToRoles = schemasVisibleToRoles;

                ArrayMap<String, Set<Integer>> schemasVisibleToPermissions =
                        new ArrayMap<>(mSchemasVisibleToPermissions.size());
                for (Map.Entry<String, Set<Integer>> entry :
                        mSchemasVisibleToPermissions.entrySet()) {
                    schemasVisibleToPermissions.put(entry.getKey(),
                            new ArraySet<>(entry.getValue()));
                }
                mSchemasVisibleToPermissions = schemasVisibleToPermissions;

                mSchemas = new ArraySet<>(mSchemas);
                mSchemasNotDisplayedBySystem = new ArraySet<>(mSchemasNotDisplayedBySystem);
                mMigrators = new ArrayMap<>(mMigrators);
                mBuilt = false;
            }
        }
    }
}
