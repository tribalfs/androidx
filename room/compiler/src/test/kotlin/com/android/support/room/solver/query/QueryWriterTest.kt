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

package com.android.support.room.solver.query

import com.android.support.room.Dao
import com.android.support.room.Query
import com.android.support.room.ext.RoomTypeNames.STRING_UTIL
import com.android.support.room.processor.QueryMethodProcessor
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.testing.TestProcessor
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.truth.Truth
import com.google.testing.compile.CompileTester
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourceSubjectFactory
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QueryWriterTest {
    companion object {
        const val DAO_PREFIX = """
                package foo.bar;
                import com.android.support.room.*;
                import java.util.*;
                @Dao
                abstract class MyClass {
                """
        const val DAO_SUFFIX = "}"
    }

    @Test
    fun simpleNoArgQuery() {
        singleQueryMethod("""
                @Query("SELECT id FROM users")
                abstract java.util.List<Integer> selectAllIds();
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(
                    """
                    java.lang.String _sql = "SELECT id FROM users";
                    java.lang.String[] _args = new String[0];
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    @Test
    fun simpleStringArgs() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE name LIKE ?")
                abstract java.util.List<Integer> selectAllIds(String name);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(
                    """
                    java.lang.String _sql = "SELECT id FROM users WHERE name LIKE ?";
                    java.lang.String[] _args = new String[1];
                    int _argIndex = 0;
                    _args[_argIndex] = name;
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    @Test
    fun twoIntArgs() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE id IN(:id1,:id2)")
                abstract java.util.List<Integer> selectAllIds(int id1, int id2);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(
                    """
                    java.lang.String _sql = "SELECT id FROM users WHERE id IN(?,?)";
                    java.lang.String[] _args = new String[2];
                    int _argIndex = 0;
                    _args[_argIndex] = java.lang.Integer.toString(id1);
                    _argIndex = 1;
                    _args[_argIndex] = java.lang.Integer.toString(id2);
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    @Test
    fun aLongAndIntVarArg() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE id IN(:ids) AND age > :time")
                abstract java.util.List<Integer> selectAllIds(long time, int... ids);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(
                    """
                    java.lang.StringBuilder _stringBuilder = $STRING_UTIL.newStringBuilder();
                    _stringBuilder.append("SELECT id FROM users WHERE id IN(");
                    final int _inputSize = ids.length;
                    $STRING_UTIL.appendPlaceholders(_stringBuilder, _inputSize);
                    _stringBuilder.append(") AND age > ");
                    _stringBuilder.append("?");
                    java.lang.String _sql = _stringBuilder.toString();
                    final int _argCount = 1 + _inputSize;
                    java.lang.String[] _args = new String[_argCount];
                    int _argIndex = 0;
                    for (int _item : ids) {
                      _args[_argIndex] = java.lang.Integer.toString(_item);
                      _argIndex ++;
                    }
                    _argIndex = _inputSize;
                    _args[_argIndex] = java.lang.Long.toString(time);
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    val collectionOut = """
                    java.lang.StringBuilder _stringBuilder = $STRING_UTIL.newStringBuilder();
                    _stringBuilder.append("SELECT id FROM users WHERE id IN(");
                    final int _inputSize = ids.size();
                    $STRING_UTIL.appendPlaceholders(_stringBuilder, _inputSize);
                    _stringBuilder.append(") AND age > ");
                    _stringBuilder.append("?");
                    java.lang.String _sql = _stringBuilder.toString();
                    final int _argCount = 1 + _inputSize;
                    java.lang.String[] _args = new String[_argCount];
                    int _argIndex = 0;
                    for (java.lang.Integer _item : ids) {
                      _args[_argIndex] = _item == null ? null : java.lang.Integer.toString(_item);
                      _argIndex ++;
                    }
                    _argIndex = _inputSize;
                    _args[_argIndex] = java.lang.Long.toString(time);
                    """.trimIndent()

    @Test
    fun aLongAndIntegerList() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE id IN(:ids) AND age > :time")
                abstract List<Integer> selectAllIds(long time, List<Integer> ids);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(collectionOut))
        }.compilesWithoutError()
    }

    @Test
    fun aLongAndIntegerSet() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE id IN(:ids) AND age > :time")
                abstract List<Integer> selectAllIds(long time, Set<Integer> ids);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`(collectionOut))
        }.compilesWithoutError()
    }

    @Test
    fun testMultipleBindParamsWithSameName() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE age > :age OR bage > :age")
                abstract List<Integer> selectAllIds(int age);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`("""
                    java.lang.String _sql = "SELECT id FROM users WHERE age > ? OR bage > ?";
                    java.lang.String[] _args = new String[2];
                    int _argIndex = 0;
                    _args[_argIndex] = java.lang.Integer.toString(age);
                    _argIndex = 1;
                    _args[_argIndex] = java.lang.Integer.toString(age);
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    @Test
    fun testMultipleBindParamsWithSameNameWithVarArg() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE age > :age OR bage > :age OR fage IN(:ages)")
                abstract List<Integer> selectAllIds(int age, int... ages);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`("""
                    java.lang.StringBuilder _stringBuilder = $STRING_UTIL.newStringBuilder();
                    _stringBuilder.append("SELECT id FROM users WHERE age > ");
                    _stringBuilder.append("?");
                    _stringBuilder.append(" OR bage > ");
                    _stringBuilder.append("?");
                    _stringBuilder.append(" OR fage IN(");
                    final int _inputSize = ages.length;
                    $STRING_UTIL.appendPlaceholders(_stringBuilder, _inputSize);
                    _stringBuilder.append(")");
                    java.lang.String _sql = _stringBuilder.toString();
                    final int _argCount = 2 + _inputSize;
                    java.lang.String[] _args = new String[_argCount];
                    int _argIndex = 0;
                    _args[_argIndex] = java.lang.Integer.toString(age);
                    _argIndex = 1;
                    _args[_argIndex] = java.lang.Integer.toString(age);
                    _argIndex = 2;
                    for (int _item : ages) {
                      _args[_argIndex] = java.lang.Integer.toString(_item);
                      _argIndex ++;
                    }
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    @Test
    fun testMultipleBindParamsWithSameNameWithVarArgInTwoBindings() {
        singleQueryMethod("""
                @Query("SELECT id FROM users WHERE age IN (:ages) OR bage > :age OR fage IN(:ages)")
                abstract List<Integer> selectAllIds(int age, int... ages);
                """) { writer ->
            val scope = CodeGenScope()
            writer.prepareReadQuery("_sql", "_args", scope)
            assertThat(scope.generate().trim(), `is`("""
                    java.lang.StringBuilder _stringBuilder = $STRING_UTIL.newStringBuilder();
                    _stringBuilder.append("SELECT id FROM users WHERE age IN (");
                    final int _inputSize = ages.length;
                    $STRING_UTIL.appendPlaceholders(_stringBuilder, _inputSize);
                    _stringBuilder.append(") OR bage > ");
                    _stringBuilder.append("?");
                    _stringBuilder.append(" OR fage IN(");
                    final int _inputSize_1 = ages.length;
                    $STRING_UTIL.appendPlaceholders(_stringBuilder, _inputSize_1);
                    _stringBuilder.append(")");
                    java.lang.String _sql = _stringBuilder.toString();
                    final int _argCount = 1 + _inputSize + _inputSize_1;
                    java.lang.String[] _args = new String[_argCount];
                    int _argIndex = 0;
                    for (int _item : ages) {
                      _args[_argIndex] = java.lang.Integer.toString(_item);
                      _argIndex ++;
                    }
                    _argIndex = _inputSize;
                    _args[_argIndex] = java.lang.Integer.toString(age);
                    _argIndex = 1 + _inputSize;
                    for (int _item_1 : ages) {
                      _args[_argIndex] = java.lang.Integer.toString(_item_1);
                      _argIndex ++;
                    }
                    """.trimIndent()))
        }.compilesWithoutError()
    }

    fun singleQueryMethod(vararg input: String,
                          handler: (QueryWriter) -> Unit):
            CompileTester {
        return Truth.assertAbout(JavaSourceSubjectFactory.javaSource())
                .that(JavaFileObjects.forSourceString("foo.bar.MyClass",
                        DAO_PREFIX + input.joinToString("\n") + DAO_SUFFIX
                ))
                .processedWith(TestProcessor.builder()
                        .forAnnotations(Query::class, Dao::class)
                        .nextRunHandler { invocation ->
                            val (owner, methods) = invocation.roundEnv
                                    .getElementsAnnotatedWith(Dao::class.java)
                                    .map {
                                        Pair(it,
                                                invocation.processingEnv.elementUtils
                                                        .getAllMembers(MoreElements.asType(it))
                                                        .filter {
                                                            MoreElements.isAnnotationPresent(it,
                                                                    Query::class.java)
                                                        }
                                        )
                                    }.filter { it.second.isNotEmpty() }.first()
                            val parser = QueryMethodProcessor(invocation.context)
                            val parsedQuery = parser.parse(MoreTypes.asDeclared(owner.asType()),
                                    MoreElements.asExecutable(methods.first()))
                            handler(QueryWriter(parsedQuery))
                            true
                        }
                        .build())
    }
}
