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

package androidx.datastore.rxjava3

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.createDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.rx3.asCoroutineDispatcher
import kotlinx.coroutines.rx3.await
import java.io.File
import java.util.concurrent.Callable

/**
 * RxSharedPreferencesMigrationBuilder class for a DataStore that works on a single process.
 */
@SuppressLint("TopLevelBuilder")
public class RxDataStoreBuilder<T> {

    /**
     * Create a RxDataStoreBuilder with the callable which returns the File that DataStore acts on.
     * The user is responsible for ensuring that there is never more than one DataStore acting on
     * a file at a time.
     *
     * @param produceFile Function which returns the file that the new DataStore will act on. The
     * function must return the same path every time. No two instances of DataStore should act on
     * the same file at the same time.
     * @param serializer the serializer for the type that this DataStore acts on.
     */
    public constructor(produceFile: Callable<File>, serializer: Serializer<T>) {
        this.produceFile = produceFile
        this.serializer = serializer
    }

    /**
     * Create a RxDataStoreBuilder with the Context and name from which to derive the DataStore
     * file. The file is generated by See [Context.createDataStore] for more info. The user is
     * responsible for ensuring that there is never more than one DataStore acting on a file at a
     * time.
     *
     * @param context the context from which we retrieve files directory.
     * @param fileName the filename relative to Context.filesDir that DataStore acts on. The File is
     * obtained by calling File(context.filesDir, "datastore/" + fileName). No two instances of
     * DataStore should act on the same file at the same time.
     * @param serializer the serializer for the type that this DataStore acts on.
     */
    public constructor(context: Context, fileName: String, serializer: Serializer<T>) {
        this.context = context
        this.name = fileName
        this.serializer = serializer
    }

    // Either produceFile or context & name must be set, but not both. This is enforced by the
    // two constructors.
    private var produceFile: Callable<File>? = null

    private var context: Context? = null
    private var name: String? = null

    // Required. This is enforced by the constructors.
    private var serializer: Serializer<T>? = null

    // Optional
    private var ioScheduler: Scheduler = Schedulers.io()
    private var corruptionHandler: ReplaceFileCorruptionHandler<T>? = null
    private val dataMigrations: MutableList<DataMigration<T>> = mutableListOf()

    /**
     * Set the Scheduler on which to perform IO and transform operations. This is converted into
     * a CoroutineDispatcher before being added to DataStore.
     *
     * This parameter is optional and defaults to Schedulers.io().
     *
     * @param ioScheduler the scheduler on which IO and transform operations are run
     * @return this
     */
    @Suppress("MissingGetterMatchingBuilder")
    public fun setIoScheduler(ioScheduler: Scheduler): RxDataStoreBuilder<T> =
        apply { this.ioScheduler = ioScheduler }

    /**
     * Sets the corruption handler to install into the DataStore.
     *
     * This parameter is optional and defaults to no corruption handler.
     *
     * @param corruptionHandler
     * @return this
     */
    @Suppress("MissingGetterMatchingBuilder")
    public fun setCorruptionHandler(corruptionHandler: ReplaceFileCorruptionHandler<T>):
        RxDataStoreBuilder<T> = apply { this.corruptionHandler = corruptionHandler }

    /**
     * Add an RxDataMigration to the DataStore. Migrations are run in the order they are added.
     *
     * @param rxDataMigration the migration to add.
     * @return this
     */
    @Suppress("MissingGetterMatchingBuilder")
    public fun addRxDataMigration(rxDataMigration: RxDataMigration<T>): RxDataStoreBuilder<T> =
        apply {
            this.dataMigrations.add(DataMigrationFromRxDataMigration(rxDataMigration))
        }

    /**
     * Add a DataMigration to the Datastore. Migrations are run in the order they are added.
     *
     * @param dataMigration the migration to add
     * @return this
     */
    @Suppress("MissingGetterMatchingBuilder")
    public fun addDataMigration(dataMigration: DataMigration<T>): RxDataStoreBuilder<T> = apply {
        this.dataMigrations.add(dataMigration)
    }

    /**
     * Build the DataStore.
     *
     * @return the DataStore with the provided parameters
     */
    public fun build(): DataStore<T> {
        val scope = CoroutineScope(ioScheduler.asCoroutineDispatcher())

        return if (produceFile != null) {
            DataStoreFactory.create(
                produceFile = { produceFile!!.call() },
                serializer = serializer!!,
                scope = CoroutineScope(
                    ioScheduler.asCoroutineDispatcher()
                ),
                corruptionHandler = corruptionHandler,
                migrations = dataMigrations
            )
        } else if (context != null && name != null) {
            return context!!.createDataStore(
                fileName = name!!,
                serializer = serializer!!,
                scope = scope,
                corruptionHandler = corruptionHandler,
                migrations = dataMigrations
            )
        } else {
            error(
                "Either produceFile or context and name must be set. This should never happen."
            )
        }
    }
}

internal class DataMigrationFromRxDataMigration<T>(private val migration: RxDataMigration<T>) :
    DataMigration<T> {
    override suspend fun shouldMigrate(currentData: T): Boolean {
        return migration.shouldMigrate(currentData).await()
    }

    override suspend fun migrate(currentData: T): T {
        return migration.migrate(currentData).await()
    }

    override suspend fun cleanUp() {
        migration.cleanUp().await()
    }
}
