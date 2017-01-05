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

package com.android.support.room.parser

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SqlParserTest {

    @Test
    fun multipleQueries() {
        assertErrors("SELECT * FROM users; SELECT * FROM books;",
                ParserErrors.NOT_ONE_QUERY)
    }

    @Test
    fun empty() {
        assertErrors("", ParserErrors.NOT_ONE_QUERY)
    }

    @Test
    fun deleteQuery() {
        val parsed = SqlParser.parse("DELETE FROM users where id > 3")
        assertThat(parsed.errors, `is`(emptyList()))
        assertThat(parsed.type, `is`(QueryType.DELETE))
    }

    @Test
    fun badDeleteQuery() {
        assertErrors("delete from user where mAge >= :min && mAge <= :max",
                "no viable alternative at input 'delete from user where mAge >= :min &&'")
    }

    @Test
    fun updateQuery() {
        val parsed = SqlParser.parse("UPDATE users set name = :name where id = :id")
        assertThat(parsed.errors, `is`(emptyList()))
        assertThat(parsed.type, `is`(QueryType.UPDATE))
    }

    @Test
    fun explain() {
        assertErrors("EXPLAIN QUERY PLAN SELECT * FROM users",
                ParserErrors.invalidQueryType(QueryType.EXPLAIN))
    }

    @Test
    fun extractTableNames() {
        assertThat(SqlParser.parse("select * from users").tables,
                `is`(setOf(Table("users", "users"))))
        assertThat(SqlParser.parse("select * from users as ux").tables,
                `is`(setOf(Table("users", "ux"))))
        assertThat(SqlParser.parse("select * from (select * from books)").tables,
                `is`(setOf(Table("books", "books"))))
        assertThat(SqlParser.parse("select x.id from (select * from books) as x").tables,
                `is`(setOf(Table("books", "books"))))
    }

    @Test
    fun findBindVariables() {
        assertVariables("select * from users")
        assertVariables("select * from users where name like ?", "?")
        assertVariables("select * from users where name like :name", ":name")
        assertVariables("select * from users where name like ?2", "?2")
        assertVariables("select * from users where name like ?2 OR name LIKE ?1", "?2", "?1")
        assertVariables("select * from users where name like @a", "@a")
        assertVariables("select * from users where name like \$a", "\$a")
    }

    @Test
    fun indexedVariablesError() {
        assertErrors("select * from users where name like ?")
        assertErrors("select * from users where name like ? or last_name like ?",
                ParserErrors.TOO_MANY_UNNAMED_VARIABLES)
        assertErrors("select * from users where name like ?1",
                ParserErrors.cannotUseVariableIndices("?1", 36))
    }

    @Test
    fun foo() {
        assertSections("select * from users where name like ?",
                Section.text("select * from users where name like "),
                Section.bindVar("?"))

        assertSections("select * from users where name like :name AND last_name like :lastName",
                Section.text("select * from users where name like "),
                Section.bindVar(":name"),
                Section.text(" AND last_name like "),
                Section.bindVar(":lastName"))

        assertSections("select * from users where name \nlike :name AND last_name like :lastName",
                Section.text("select * from users where name "),
                Section.newline(),
                Section.text("like "),
                Section.bindVar(":name"),
                Section.text(" AND last_name like "),
                Section.bindVar(":lastName"))

        assertSections("select * from users where name like :name \nAND last_name like :lastName",
                Section.text("select * from users where name like "),
                Section.bindVar(":name"),
                Section.text(" "),
                Section.newline(),
                Section.text("AND last_name like "),
                Section.bindVar(":lastName"))

        assertSections("select * from users where name like :name \nAND last_name like \n:lastName",
                Section.text("select * from users where name like "),
                Section.bindVar(":name"),
                Section.text(" "),
                Section.newline(),
                Section.text("AND last_name like "),
                Section.newline(),
                Section.bindVar(":lastName"))
    }

    fun assertVariables(query: String, vararg expected: String) {
        assertThat((SqlParser.parse(query)).inputs.map { it.text }, `is`(expected.toList()))
    }

    fun assertErrors(query: String, vararg errors: String) {
        assertThat((SqlParser.parse(query)).errors, `is`(errors.toList()))
    }

    fun assertSections(query: String, vararg sections: Section) {
        assertThat(SqlParser.parse(query).sections, `is`(sections.toList()))
    }
}
