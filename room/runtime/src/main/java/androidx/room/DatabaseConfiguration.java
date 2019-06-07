/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.room;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Configuration class for a {@link RoomDatabase}.
 */
@SuppressWarnings("WeakerAccess")
public class DatabaseConfiguration {

    /**
     * Copy indicator for when Room is initially opening the database.
     */
    @IntDef({COPY_FROM_NONE, COPY_FROM_ASSET, COPY_FROM_FILE})
    public @interface CopyFrom {}

    /**
     * No database will be copied.
     */
    public static final int COPY_FROM_NONE = 0;

    /**
     * Pre-packaged database should be copied from the application assets folder.
     */
    public static final int COPY_FROM_ASSET = 1;

    /**
     * Pre-packaged database should be copied from an external file.
     */
    public static final int COPY_FROM_FILE = 2;

    /**
     * The factory to use to access the database.
     */
    @NonNull
    public final SupportSQLiteOpenHelper.Factory sqliteOpenHelperFactory;
    /**
     * The context to use while connecting to the database.
     */
    @NonNull
    public final Context context;
    /**
     * The name of the database file or null if it is an in-memory database.
     */
    @Nullable
    public final String name;

    /**
     * Collection of available migrations.
     */
    @NonNull
    public final RoomDatabase.MigrationContainer migrationContainer;

    @Nullable
    public final List<RoomDatabase.Callback> callbacks;

    /**
     * Whether Room should throw an exception for queries run on the main thread.
     */
    public final boolean allowMainThreadQueries;

    /**
     * The journal mode for this database.
     */
    public final RoomDatabase.JournalMode journalMode;

    /**
     * The Executor used to execute asynchronous queries.
     */
    @NonNull
    public final Executor queryExecutor;

    /**
     * The Executor used to execute asynchronous transactions.
     */
    @NonNull
    public final Executor transactionExecutor;

    /**
     * If true, table invalidation in an instance of {@link RoomDatabase} is broadcast and
     * synchronized with other instances of the same {@link RoomDatabase} file, including those
     * in a separate process.
     */
    public final boolean multiInstanceInvalidation;

    /**
     * If true, Room should crash if a migration is missing.
     */
    public final boolean requireMigration;

    /**
     * If true, Room should perform a destructive migration when downgrading without an available
     * migration.
     */
    public final boolean allowDestructiveMigrationOnDowngrade;

    /**
     * The collection of schema versions from which migrations aren't required.
     */
    private final Set<Integer> mMigrationNotRequiredFrom;

    /**
     * Whether Room should copy or not a pre-packaged database when it is first created.
     */
    public final @CopyFrom int copyFrom;

    /**
     * The path to a pre-packaged database to copy from.
     */
    @Nullable
    public final String copyFromPath;


    /**
     * Creates a database configuration with the given values.
     *
     * @deprecated Use {@link #DatabaseConfiguration(Context, String,
     * SupportSQLiteOpenHelper.Factory, RoomDatabase.MigrationContainer, List, boolean,
     * RoomDatabase.JournalMode, Executor, Executor, boolean, boolean, boolean, Set, int, String)}
     *
     * @param context The application context.
     * @param name Name of the database, can be null if it is in memory.
     * @param sqliteOpenHelperFactory The open helper factory to use.
     * @param migrationContainer The migration container for migrations.
     * @param callbacks The list of callbacks for database events.
     * @param allowMainThreadQueries Whether to allow main thread reads/writes or not.
     * @param journalMode The journal mode. This has to be either TRUNCATE or WRITE_AHEAD_LOGGING.
     * @param queryExecutor The Executor used to execute asynchronous queries.
     * @param requireMigration True if Room should require a valid migration if version changes,
     *                        instead of recreating the tables.
     * @param migrationNotRequiredFrom The collection of schema versions from which migrations
     *                                 aren't required.
     *
     * @hide
     */
    @Deprecated
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public DatabaseConfiguration(@NonNull Context context, @Nullable String name,
            @NonNull SupportSQLiteOpenHelper.Factory sqliteOpenHelperFactory,
            @NonNull RoomDatabase.MigrationContainer migrationContainer,
            @Nullable List<androidx.room.RoomDatabase.Callback> callbacks,
            boolean allowMainThreadQueries,
            RoomDatabase.JournalMode journalMode,
            @NonNull Executor queryExecutor,
            boolean requireMigration,
            @Nullable Set<Integer> migrationNotRequiredFrom) {
        this(context, name, sqliteOpenHelperFactory, migrationContainer, callbacks,
                allowMainThreadQueries, journalMode, queryExecutor, queryExecutor, false,
                requireMigration, false, migrationNotRequiredFrom, COPY_FROM_NONE, null);
    }

    /**
     * Creates a database configuration with the given values.
     *
     * @deprecated Use {@link #DatabaseConfiguration(Context, String,
     * SupportSQLiteOpenHelper.Factory, RoomDatabase.MigrationContainer, List, boolean,
     * RoomDatabase.JournalMode, Executor, Executor, boolean, boolean, boolean, Set, int, String)}
     *
     * @param context The application context.
     * @param name Name of the database, can be null if it is in memory.
     * @param sqliteOpenHelperFactory The open helper factory to use.
     * @param migrationContainer The migration container for migrations.
     * @param callbacks The list of callbacks for database events.
     * @param allowMainThreadQueries Whether to allow main thread reads/writes or not.
     * @param journalMode The journal mode. This has to be either TRUNCATE or WRITE_AHEAD_LOGGING.
     * @param queryExecutor The Executor used to execute asynchronous queries.
     * @param transactionExecutor The Executor used to execute asynchronous transactions.
     * @param multiInstanceInvalidation True if Room should perform multi-instance invalidation.
     * @param requireMigration True if Room should require a valid migration if version changes,
     * @param allowDestructiveMigrationOnDowngrade True if Room should recreate tables if no
     *                                             migration is supplied during a downgrade.
     * @param migrationNotRequiredFrom The collection of schema versions from which migrations
     *                                 aren't required.
     *
     * @hide
     */
    @Deprecated
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public DatabaseConfiguration(@NonNull Context context, @Nullable String name,
            @NonNull SupportSQLiteOpenHelper.Factory sqliteOpenHelperFactory,
            @NonNull RoomDatabase.MigrationContainer migrationContainer,
            @Nullable List<RoomDatabase.Callback> callbacks,
            boolean allowMainThreadQueries,
            RoomDatabase.JournalMode journalMode,
            @NonNull Executor queryExecutor,
            @NonNull Executor transactionExecutor,
            boolean multiInstanceInvalidation,
            boolean requireMigration,
            boolean allowDestructiveMigrationOnDowngrade,
            @Nullable Set<Integer> migrationNotRequiredFrom) {
        this(context, name, sqliteOpenHelperFactory, migrationContainer, callbacks,
                allowMainThreadQueries, journalMode, queryExecutor, transactionExecutor,
                multiInstanceInvalidation, requireMigration, allowDestructiveMigrationOnDowngrade,
                migrationNotRequiredFrom, COPY_FROM_NONE, null);
    }

    /**
     * Creates a database configuration with the given values.
     *
     * @param context The application context.
     * @param name Name of the database, can be null if it is in memory.
     * @param sqliteOpenHelperFactory The open helper factory to use.
     * @param migrationContainer The migration container for migrations.
     * @param callbacks The list of callbacks for database events.
     * @param allowMainThreadQueries Whether to allow main thread reads/writes or not.
     * @param journalMode The journal mode. This has to be either TRUNCATE or WRITE_AHEAD_LOGGING.
     * @param queryExecutor The Executor used to execute asynchronous queries.
     * @param transactionExecutor The Executor used to execute asynchronous transactions.
     * @param multiInstanceInvalidation True if Room should perform multi-instance invalidation.
     * @param requireMigration True if Room should require a valid migration if version changes,
     * @param allowDestructiveMigrationOnDowngrade True if Room should recreate tables if no
     *                                             migration is supplied during a downgrade.
     * @param migrationNotRequiredFrom The collection of schema versions from which migrations
     *                                 aren't required.
     * @param copyFrom If Room should copy a pre-packaged database on initial create. If set to
     *                 anything other than COPY_FROM_NONE, then path to copy from should be set into
     *                 the {@code copyFromPath} parameter.
     * @param copyFromPath Path to the pre-packaged database.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public DatabaseConfiguration(@NonNull Context context, @Nullable String name,
            @NonNull SupportSQLiteOpenHelper.Factory sqliteOpenHelperFactory,
            @NonNull RoomDatabase.MigrationContainer migrationContainer,
            @Nullable List<RoomDatabase.Callback> callbacks,
            boolean allowMainThreadQueries,
            RoomDatabase.JournalMode journalMode,
            @NonNull Executor queryExecutor,
            @NonNull Executor transactionExecutor,
            boolean multiInstanceInvalidation,
            boolean requireMigration,
            boolean allowDestructiveMigrationOnDowngrade,
            @Nullable Set<Integer> migrationNotRequiredFrom,
            @CopyFrom int copyFrom,
            @Nullable String copyFromPath) {
        this.sqliteOpenHelperFactory = sqliteOpenHelperFactory;
        this.context = context;
        this.name = name;
        this.migrationContainer = migrationContainer;
        this.callbacks = callbacks;
        this.allowMainThreadQueries = allowMainThreadQueries;
        this.journalMode = journalMode;
        this.queryExecutor = queryExecutor;
        this.transactionExecutor = transactionExecutor;
        this.multiInstanceInvalidation = multiInstanceInvalidation;
        this.requireMigration = requireMigration;
        this.allowDestructiveMigrationOnDowngrade = allowDestructiveMigrationOnDowngrade;
        this.mMigrationNotRequiredFrom = migrationNotRequiredFrom;
        this.copyFrom = copyFrom;
        this.copyFromPath = copyFromPath;
    }

    /**
     * Returns whether a migration is required from the specified version.
     *
     * @param version  The schema version.
     * @return True if a valid migration is required, false otherwise.
     *
     * @deprecated Use {@link #isMigrationRequired(int, int)} which takes
     * {@link #allowDestructiveMigrationOnDowngrade} into account.
     */
    @Deprecated
    public boolean isMigrationRequiredFrom(int version) {
        return isMigrationRequired(version, version + 1);
    }

    /**
     * Returns whether a migration is required between two versions.
     *
     * @param fromVersion The old schema version.
     * @param toVersion   The new schema version.
     * @return True if a valid migration is required, false otherwise.
     */
    public boolean isMigrationRequired(int fromVersion, int toVersion) {
        // Migrations are not required if its a downgrade AND destructive migration during downgrade
        // has been allowed.
        final boolean isDowngrade = fromVersion > toVersion;
        if (isDowngrade && allowDestructiveMigrationOnDowngrade) {
            return false;
        }

        // Migrations are required between the two versions if we generally require migrations
        // AND EITHER there are no exceptions OR the supplied fromVersion is not one of the
        // exceptions.
        return requireMigration
                && (mMigrationNotRequiredFrom == null
                || !mMigrationNotRequiredFrom.contains(fromVersion));
    }
}
