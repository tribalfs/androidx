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
package androidx.sqlite.db

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import java.io.File

/**
 * Helper for accessing features in [SupportSQLiteOpenHelper].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class SupportSQLiteCompat private constructor() {
    /**
     * Class for accessing functions that require SDK version 16 and higher.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(16)
    object Api16Impl {
        /**
         * Cancels the operation and signals the cancellation listener. If the operation has not yet
         * started, then it will be canceled as soon as it does.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun cancel(cancellationSignal: CancellationSignal) {
            cancellationSignal.cancel()
        }

        /**
         * Creates a cancellation signal, initially not canceled.
         *
         * @return a new cancellation signal
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun createCancellationSignal(): CancellationSignal {
            return CancellationSignal()
        }

        /**
         * Deletes a database including its journal file and other auxiliary files
         * that may have been created by the database engine.
         *
         * @param file The database file path.
         * @return True if the database was successfully deleted.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun deleteDatabase(file: File): Boolean {
            return SQLiteDatabase.deleteDatabase(file)
        }

        /**
         * Runs the provided SQL and returns a cursor over the result set.
         *
         * @param sql the SQL query. The SQL string must not be ; terminated
         * @param selectionArgs You may include ?s in where clause in the query,
         * which will be replaced by the values from selectionArgs. The
         * values will be bound as Strings.
         * @param editTable the name of the first table, which is editable
         * @param cancellationSignal A signal to cancel the operation in progress, or null if none.
         * If the operation is canceled, then [OperationCanceledException] will be thrown
         * when the query is executed.
         * @param cursorFactory the cursor factory to use, or null for the default factory
         * @return A [Cursor] object, which is positioned before the first entry. Note that
         * [Cursor]s are not synchronized, see the documentation for more details.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun rawQueryWithFactory(
            sQLiteDatabase: SQLiteDatabase,
            sql: String,
            selectionArgs: Array<String?>,
            editTable: String?,
            cancellationSignal: CancellationSignal,
            cursorFactory: CursorFactory
        ): Cursor {
            return sQLiteDatabase.rawQueryWithFactory(
                cursorFactory, sql, selectionArgs, editTable,
                cancellationSignal
            )
        }

        /**
         * Sets whether foreign key constraints are enabled for the database.
         *
         * @param enable True to enable foreign key constraints, false to disable them.
         *
         * @throws [IllegalStateException] if the are transactions is in progress
         * when this method is called.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun setForeignKeyConstraintsEnabled(
            sQLiteDatabase: SQLiteDatabase,
            enable: Boolean
        ) {
            sQLiteDatabase.setForeignKeyConstraintsEnabled(enable)
        }

        /**
         * This method disables the features enabled by
         * [SQLiteDatabase.enableWriteAheadLogging].
         *
         * @throws - if there are transactions in progress at the
         * time this method is called.  WAL mode can only be changed when there are no
         * transactions in progress.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun disableWriteAheadLogging(sQLiteDatabase: SQLiteDatabase) {
            sQLiteDatabase.disableWriteAheadLogging()
        }

        /**
         * Returns true if [SQLiteDatabase.enableWriteAheadLogging] logging has been enabled for
         * this database.
         *
         * For details, see [SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING].
         *
         * @return True if write-ahead logging has been enabled for this database.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun isWriteAheadLoggingEnabled(sQLiteDatabase: SQLiteDatabase): Boolean {
            return sQLiteDatabase.isWriteAheadLoggingEnabled
        }

        /**
         * Sets [SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING] flag if `enabled` is `true`, unsets
         * otherwise.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun setWriteAheadLoggingEnabled(
            sQLiteOpenHelper: SQLiteOpenHelper,
            enabled: Boolean
        ) {
            sQLiteOpenHelper.setWriteAheadLoggingEnabled(enabled)
        }
    }

    /**
     * Helper for accessing functions that require SDK version 19 and higher.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(19)
    object Api19Impl {
        /**
         * Return the URI at which notifications of changes in this Cursor's data
         * will be delivered.
         *
         * @return Returns a URI that can be used with [ContentResolver.registerContentObserver] to
         * find out about changes to this Cursor's data. May be null if no notification URI has been
         * set.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun getNotificationUri(cursor: Cursor): Uri {
            return cursor.notificationUri
        }

        /**
         * Returns true if this is a low-RAM device.  Exactly whether a device is low-RAM
         * is ultimately up to the device configuration, but currently it generally means
         * something with 1GB or less of RAM.  This is mostly intended to be used by apps
         * to determine whether they should turn off certain features that require more RAM.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun isLowRamDevice(activityManager: ActivityManager): Boolean {
            return activityManager.isLowRamDevice
        }
    }

    /**
     * Helper for accessing functions that require SDK version 21 and higher.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(21)
    object Api21Impl {
        /**
         * Returns the absolute path to the directory on the filesystem.
         *
         * @return The path of the directory holding application files that will not be
         * automatically backed up to remote storage.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun getNoBackupFilesDir(context: Context): File {
            return context.noBackupFilesDir
        }
    }

    /**
     * Helper for accessing functions that require SDK version 23 and higher.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(23)
    object Api23Impl {
        /**
         * Sets a [Bundle] that will be returned by [Cursor.getExtras].
         *
         * @param extras [Bundle] to set, or null to set an empty bundle.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun setExtras(cursor: Cursor, extras: Bundle) {
            cursor.extras = extras
        }
    }

    /**
     * Helper for accessing functions that require SDK version 29 and higher.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresApi(29)
    object Api29Impl {
        /**
         * Similar to [Cursor.setNotificationUri], except this version
         * allows to watch multiple content URIs for changes.
         *
         * @param cr The content resolver from the caller's context. The listener attached to
         * this resolver will be notified.
         * @param uris The content URIs to watch.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun setNotificationUris(
            cursor: Cursor,
            cr: ContentResolver,
            uris: List<Uri?>
        ) {
            cursor.setNotificationUris(cr, uris)
        }

        /**
         * Return the URIs at which notifications of changes in this Cursor's data
         * will be delivered, as previously set by [setNotificationUris].
         *
         * @return Returns URIs that can be used with [ContentResolver.registerContentObserver]
         * to find out about changes to this Cursor's data. May be null if no notification URI has
         * been set.
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun getNotificationUris(cursor: Cursor): List<Uri> {
            return cursor.notificationUris!!
        }
    }
}