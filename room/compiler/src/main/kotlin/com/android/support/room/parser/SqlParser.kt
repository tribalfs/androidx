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

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import java.util.*

class QueryVisitor(val original: String, val syntaxErrors: ArrayList<String>,
                   statement: ParseTree) : SQLiteBaseVisitor<Void?>() {
    val bindingExpressions = arrayListOf<TerminalNode>()
    // table name alias mappings
    val tableNames = mutableSetOf<Table>()
    val queryType: QueryType

    init {
        queryType = (0..statement.childCount - 1).map {
            findQueryType(statement.getChild(it))
        }.filterNot { it == QueryType.UNKNOWN }.first()

        statement.accept(this)
    }

    private fun findQueryType(statement: ParseTree): QueryType {
        return when (statement) {
            is SQLiteParser.Factored_select_stmtContext,
            is SQLiteParser.Compound_select_stmtContext,
            is SQLiteParser.Select_stmtContext,
            is SQLiteParser.Simple_select_stmtContext ->
                QueryType.SELECT

            is SQLiteParser.Delete_stmt_limitedContext,
            is SQLiteParser.Delete_stmtContext ->
                QueryType.DELETE

            is SQLiteParser.Insert_stmtContext ->
                QueryType.INSERT
            is SQLiteParser.Update_stmtContext,
            is SQLiteParser.Update_stmt_limitedContext ->
                QueryType.UPDATE
            is TerminalNode -> when (statement.text) {
                "EXPLAIN" -> QueryType.EXPLAIN
                else -> QueryType.UNKNOWN
            }
            else -> QueryType.UNKNOWN
        }
    }

    override fun visitExpr(ctx: SQLiteParser.ExprContext): Void? {
        val bindParameter = ctx.BIND_PARAMETER()
        if (bindParameter != null) {
            bindingExpressions.add(bindParameter)
        }
        return super.visitExpr(ctx)
    }

    fun createParsedQuery(): ParsedQuery {
        return ParsedQuery(original,
                queryType,
                bindingExpressions.sortedBy { it.sourceInterval.a },
                tableNames,
                syntaxErrors)
    }

    override fun visitTable_or_subquery(ctx: SQLiteParser.Table_or_subqueryContext): Void? {
        val tableName = ctx.table_name()?.text
        if (tableName != null) {
            val tableAlias = ctx.table_alias()?.text
            tableNames.add(Table(tableName, tableAlias ?: tableName))
        }
        return super.visitTable_or_subquery(ctx)
    }
}

class SqlParser {
    companion object {
        fun parse(input: String): ParsedQuery {
            val inputStream = ANTLRInputStream(input)
            val lexer = SQLiteLexer(inputStream)
            val tokenStream = CommonTokenStream(lexer)
            val parser = SQLiteParser(tokenStream)
            val syntaxErrors = arrayListOf<String>()
            parser.addErrorListener(object : BaseErrorListener() {
                override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any,
                                         line: Int, charPositionInLine: Int, msg: String,
                                         e: RecognitionException?) {
                    syntaxErrors.add(msg)
                }
            })
            try {
                val parsed = parser.parse()
                val statementList = parsed.sql_stmt_list()
                if (statementList.isEmpty()) {
                    syntaxErrors.add(ParserErrors.NOT_ONE_QUERY)
                    return ParsedQuery(input, QueryType.UNKNOWN, emptyList(), emptySet(),
                            listOf(ParserErrors.NOT_ONE_QUERY))
                }
                val statements = statementList.first().children
                        .filter { it is SQLiteParser.Sql_stmtContext }
                if (statements.size != 1) {
                    syntaxErrors.add(ParserErrors.NOT_ONE_QUERY)
                }
                val statement = statements.first()
                return QueryVisitor(input, syntaxErrors, statement).createParsedQuery()
            } catch (antlrError: RuntimeException) {
                return ParsedQuery(input, QueryType.UNKNOWN, emptyList(), emptySet(),
                        listOf(antlrError.message ?: "unknown error while parsing $input"))
            }
        }
    }
}

enum class QueryType {
    UNKNOWN,
    SELECT,
    DELETE,
    UPDATE,
    EXPLAIN,
    INSERT;

    companion object {
        val SUPPORTED = hashSetOf(SELECT)
    }
}

enum class SQLTypeAffinity {
    TEXT,
    NUMERIC,
    INTEGER,
    REAL,
    BLOB
}
