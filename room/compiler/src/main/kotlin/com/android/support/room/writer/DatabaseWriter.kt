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

package com.android.support.room.writer

import com.android.support.room.ext.L
import com.android.support.room.ext.N
import com.android.support.room.ext.RoomTypeNames
import com.android.support.room.ext.SupportDbTypeNames
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.vo.Database
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.PUBLIC

/**
 * Writes implementation of classes that were annotated with @Database.
 */
class DatabaseWriter(val database : Database) : ClassWriter(database.implTypeName) {
    override fun createTypeSpec(): TypeSpec {
        val builder = TypeSpec.classBuilder(database.implTypeName)
        builder.apply {
            addModifiers(PUBLIC)
            superclass(database.typeName)
            addMethod(createConstructor())
            addMethod(createCreateOpenHelper())
        }
        return builder.build()
    }

    private fun createConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder().apply {
            addModifiers(Modifier.PUBLIC)
            val configParam = ParameterSpec.builder(RoomTypeNames.ROOM_DB_CONFIG,
                    "configuration").build()
            addParameter(configParam)
            addStatement("super($N)", configParam)
        }.build()
    }

    private fun createCreateOpenHelper() : MethodSpec {
        val scope = CodeGenScope()
        return MethodSpec.methodBuilder("createOpenHelper").apply {
            addModifiers(Modifier.PROTECTED)
            returns(SupportDbTypeNames.SQLITE_OPEN_HELPER)

            val configParam = ParameterSpec.builder(RoomTypeNames.ROOM_DB_CONFIG,
                    "configuration").build()
            addParameter(configParam)

            val openHelperVar = scope.getTmpVar("_helper")
            val openHelperCode = scope.fork()
            SQLiteOpenHelperWriter(database)
                    .write(openHelperVar, configParam, openHelperCode)
            addCode(openHelperCode.builder().build())
            addStatement("return $L", openHelperVar)
        }.build()
    }
}
