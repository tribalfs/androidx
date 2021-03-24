/*
 * Copyright (C) 2020 The Android Open Source Project
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

package androidx.room

import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XProcessingStep
import androidx.room.compiler.processing.XTypeElement
import androidx.room.processor.Context
import androidx.room.processor.DatabaseProcessor
import androidx.room.processor.MissingTypeException
import androidx.room.processor.ProcessorErrors
import androidx.room.vo.DaoMethod
import androidx.room.vo.Warning
import androidx.room.writer.AutoMigrationWriter
import androidx.room.writer.DaoWriter
import androidx.room.writer.DatabaseWriter
import java.io.File

class DatabaseProcessingStep : XProcessingStep {
    override fun process(
        env: XProcessingEnv,
        elementsByAnnotation: Map<String, List<XTypeElement>>
    ): Set<XTypeElement> {
        val context = Context(env)
        val rejectedElements = mutableSetOf<XTypeElement>()
        val databases = elementsByAnnotation[Database::class.qualifiedName]
            ?.mapNotNull {
                try {
                    DatabaseProcessor(
                        context,
                        it
                    ).process()
                } catch (ex: MissingTypeException) {
                    // Abandon processing this database class since it needed a type element
                    // that is missing. It is possible that the type will be generated by a
                    // further annotation processing round, so we will try again by adding
                    // this class element to a deferred set.
                    rejectedElements.add(it)
                    null
                }
            }
        val daoMethodsMap = databases?.flatMap { db -> db.daoMethods.map { it to db } }?.toMap()
        daoMethodsMap?.let {
            prepareDaosForWriting(databases, it.keys.toList())
            it.forEach { (daoMethod, db) ->
                DaoWriter(
                    daoMethod.dao,
                    db.element,
                    context.processingEnv
                )
                    .write(context.processingEnv)
            }
        }

        databases?.forEach { db ->
            DatabaseWriter(db).write(context.processingEnv)
            if (db.exportSchema) {
                val schemaOutFolder = context.schemaOutFolder
                if (schemaOutFolder == null) {
                    context.logger.w(
                        Warning.MISSING_SCHEMA_LOCATION, db.element,
                        ProcessorErrors.MISSING_SCHEMA_EXPORT_DIRECTORY
                    )
                } else {
                    if (!schemaOutFolder.exists()) {
                        schemaOutFolder.mkdirs()
                    }
                    val qName = db.element.qualifiedName
                    val dbSchemaFolder = File(schemaOutFolder, qName)
                    if (!dbSchemaFolder.exists()) {
                        dbSchemaFolder.mkdirs()
                    }
                    db.exportSchema(
                        File(
                            dbSchemaFolder,
                            "${db.version}.json"
                        )
                    )
                }
            }
            db.autoMigrations.forEach { autoMigration ->
                AutoMigrationWriter(db.element, autoMigration).write(context.processingEnv)
            }
        }
        return rejectedElements
    }

    override fun annotations(): Set<String> {
        return mutableSetOf(Database::class.qualifiedName!!)
    }

    /**
     * Traverses all dao methods and assigns them suffix if they are used in multiple databases.
     */
    private fun prepareDaosForWriting(
        databases: List<androidx.room.vo.Database>,
        daoMethods: List<DaoMethod>
    ) {
        daoMethods.groupBy { it.dao.typeName }
            // if used only in 1 database, nothing to do.
            .filter { entry -> entry.value.size > 1 }
            .forEach { entry ->
                entry.value.groupBy { daoMethod ->
                    // first suffix guess: Database's simple name
                    val db = databases.first { db -> db.daoMethods.contains(daoMethod) }
                    db.typeName.simpleName()
                }.forEach { (dbName, methods) ->
                    if (methods.size == 1) {
                        // good, db names do not clash, use db name as suffix
                        methods.first().dao.setSuffix(dbName)
                    } else {
                        // ok looks like a dao is used in 2 different databases both of
                        // which have the same name. enumerate.
                        methods.forEachIndexed { index, method ->
                            method.dao.setSuffix("${dbName}_$index")
                        }
                    }
                }
            }
    }
}