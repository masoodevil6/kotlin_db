package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.models.eloquent.modules.users.Users
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

@QBTable(name = "test_users", alias = "tu")
private class TestUser : IModelBase {
    @QBColumn(name = "user_name", alias = "user_name_default")
    val userName: String? = null

    @QBColumn(name = "plain_name")
    val plainName: String? = null
}

private class MissingTableModel : IModelBase

@QBTable(name = "", alias = "")
private class EmptyTableModel : IModelBase

@QBTable(name = "missing_column_table")
private class MissingColumnAnnotationModel : IModelBase {
    val value: String? = null
}

@QBTable(name = "empty_column_table")
private class EmptyColumnNameModel : IModelBase {
    @QBColumn(name = "")
    val value: String? = null
}

@QBTable(name = "typed_values")
private class TypedValueModel : IModelBase {
    @QBColumn(name = "int_value")
    val intValue: Int = 0

    @QBColumn(name = "nullable_int_value")
    val nullableIntValue: Int? = null

    @QBColumn(name = "long_value")
    val longValue: Long = 0L

    @QBColumn(name = "nullable_long_value")
    val nullableLongValue: Long? = null

    @QBColumn(name = "string_value")
    val stringValue: String = ""

    @QBColumn(name = "nullable_string_value")
    val nullableStringValue: String? = null

    @QBColumn(name = "unsupported_value")
    val unsupportedValue: Boolean = false
}

class TypedModelReferenceTest {

    @Test
    fun `direct model property infers supported execution types including nullable properties`() {
        val columns = listOf(
            TypedValueModel::intValue to DataType.INT,
            TypedValueModel::nullableIntValue to DataType.INT,
            TypedValueModel::longValue to DataType.LONG,
            TypedValueModel::nullableLongValue to DataType.LONG,
            TypedValueModel::stringValue to DataType.STRING,
            TypedValueModel::nullableStringValue to DataType.STRING,
        )

        columns.forEach { (property, expectedType) ->
            val query = QueryRenderSelectBuilder().select {
                addColumn { column { tableSelector(TypedValueModel::class); columnSelector(TypedValueModel::class, property) } }
            }
            val output = query.ast.select!!.columns.single()
            assertEquals(expectedType, output.metadata.ExecutionType, property.name)
            assertEquals(property, output.metadata.PropertyReference, property.name)
        }
    }

    @Test
    fun `explicit execution type wins before or after model column declaration`() {
        val columnThenExecute = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableSelector(TypedValueModel::class); columnSelector(TypedValueModel::class, TypedValueModel::intValue) }
                execute(DataType.LONG)
            }
        }.ast.select!!.columns.single()

        val executeThenColumn = QueryRenderSelectBuilder().select {
            addColumn {
                execute(DataType.LONG)
                column { tableSelector(TypedValueModel::class); columnSelector(TypedValueModel::class, TypedValueModel::intValue) }
            }
        }.ast.select!!.columns.single()

        assertEquals(DataType.LONG, columnThenExecute.metadata.ExecutionType)
        assertEquals(DataType.LONG, executeThenColumn.metadata.ExecutionType)
    }

    @Test
    fun `unsupported and non-model outputs do not infer execution type`() {
        val unsupported = QueryRenderSelectBuilder().select {
            addColumn { column { tableSelector(TypedValueModel::class); columnSelector(TypedValueModel::class, TypedValueModel::unsupportedValue) } }
        }.ast.select!!.columns.single()
        assertNull(unsupported.metadata.ExecutionType)

        val raw = QueryRenderSelectBuilder().select {
            addColumn { column { tableColumn("u", "id") } }
            addColumn { column { tableAttribute("count(*)") } }
            addColumn {
                methodCount { addColumn { tableSelector("u"); columnSelector("id") } }
            }
        }.ast.select!!.columns

        raw.forEach { output ->
            assertNull(output.metadata.ExecutionType)
            assertNull(output.metadata.PropertyReference)
        }

        val explicitUnsupported = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableSelector(TypedValueModel::class); columnSelector(TypedValueModel::class, TypedValueModel::unsupportedValue) }
                execute(DataType.STRING)
            }
        }.ast.select!!.columns.single()
        assertEquals(DataType.STRING, explicitUnsupported.metadata.ExecutionType)
    }

    @Test
    fun `count output does not infer result type from its model argument`() {
        val aggregate = QueryRenderSelectBuilder().select {
            addColumn {
                methodCount {
                    addColumn {
                        tableSelector(TypedValueModel::class)
                        columnSelector(TypedValueModel::class, TypedValueModel::intValue)
                    }
                }
            }
        }.ast.select!!.columns.single()

        assertNull(aggregate.metadata.ExecutionType)
        assertNull(aggregate.metadata.PropertyReference)

        val explicitAggregate = QueryRenderSelectBuilder().select {
            addColumn {
                methodCount {
                    addColumn {
                        tableSelector(TypedValueModel::class)
                        columnSelector(TypedValueModel::class, TypedValueModel::intValue)
                    }
                }
                execute(DataType.LONG)
            }
        }.ast.select!!.columns.single()
        assertEquals(DataType.LONG, explicitAggregate.metadata.ExecutionType)
        assertNull(explicitAggregate.metadata.PropertyReference)
    }

    @Test
    fun `source alias is independent of table and cte declaration order`() {
        val tableQuery = QueryRenderSelectBuilder().from {
            alias("shared")
            table("users")
        }
        val cteQuery = QueryRenderSelectBuilder().from {
            cte("user_cte")
            alias("shared")
        }

        assertEquals("shared", tableQuery.ast.table!!.metadata.aliasFromAst)
        assertEquals("shared", cteQuery.ast.table!!.metadata.aliasFromAst)
    }

    @Test
    fun `string source can be declared without an alias`() {
        val query = QueryRenderSelectBuilder().from {
            table("users")
        }

        assertNull(query.ast.table!!.metadata.aliasFromAst)
    }

    @Test
    fun `typed reference resolves metadata and applies later table alias override`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                }
            }
            .from {
                model(TestUser::class).alias("u")
            }

        val table = query.ast.table!!
        val column = query.ast.select!!.columns.single()

        assertEquals("test_user", table.metadata.nameFromModel)
        assertEquals("test_users", table.metadata.nameFromAnnotation)
        assertEquals("tu", table.metadata.aliasFromAnnotation)
        assertEquals("u", table.metadata.aliasFromAst)
        assertEquals("u", column.Column!!.metadata.resolveTableAlias())
        assertEquals("user_name", column.Column!!.metadata.resolveColumnName())
        assertEquals("test_user", column.Column!!.metadata.tableAliasFromModel)
        assertEquals("tu", column.Column!!.metadata.tableAliasFromAnnotation)
        assertEquals("u", column.Column!!.metadata.tableAliasFromAst)
        assertEquals("userName", column.Column!!.metadata.columnNameFromModel)
        assertEquals("user_name", column.Column!!.metadata.columnNameFromAnnotation)
        assertNull(column.Column!!.metadata.columnNameFromAst)
        assertEquals("userName", column.Column!!.metadata.columnAliasFromModel)
        assertEquals("user_name_default", column.Column!!.metadata.columnAliasFromAnnotation)
        assertEquals("user_name_default", column.metadata.ColumnAlias)
    }

    @Test
    fun `typed column uses table and select defaults`() {
        val query = QueryRenderSelectBuilder()
            .from { model(TestUser::class) }
            .select {
                addColumn {
                    column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                }
            }

        val column = query.ast.select!!.columns.single()
        assertEquals("tu", query.ast.table!!.metadata.aliasFromAnnotation)
        assertNull(query.ast.table!!.metadata.aliasFromAst)
        assertEquals("tu", column.Column!!.metadata.resolveTableAlias())
        assertEquals("test_user", column.Column!!.metadata.tableAliasFromModel)
        assertEquals("tu", column.Column!!.metadata.tableAliasFromAnnotation)
        assertNull(column.Column!!.metadata.tableAliasFromAst)
        assertEquals("user_name_default", column.Column!!.metadata.resolveColumnAlias())
        assertEquals("user_name_default", column.metadata.ColumnAlias)
    }

    @Test
    fun `selectors resolve model and SQL sources into the common column metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { model(TestUser::class).alias("u") }
            .select {
                addColumn {
                    column {
                        tableSelector(TestUser::class)
                        columnSelector(TestUser::userName)
                    }
                }
                addColumn {
                    column {
                        tableSelector("test_users")
                        columnSelector("plain_name")
                    }
                    alias("raw_name")
                    execute(DataType.STRING)
                }
                addColumn {
                    column { columnSelector("count(*)") }
                    alias("row_count")
                }
            }

        val outputs = query.ast.select!!.columns
        assertEquals(TestUser::userName, outputs[0].metadata.PropertyReference)
        assertEquals(DataType.STRING, outputs[0].metadata.ExecutionType)
        assertEquals("user_name_default", outputs[0].metadata.ColumnAlias)
        assertEquals("u", outputs[0].Column!!.metadata.resolveTableAlias())
        assertEquals("userName", outputs[0].Column!!.metadata.columnNameFromModel)
        assertEquals("user_name", outputs[0].Column!!.metadata.columnNameFromAnnotation)
        assertEquals("plain_name", outputs[1].Column!!.metadata.resolveColumnName())
        assertEquals("u", outputs[1].Column!!.metadata.resolveTableAlias())
        assertEquals("test_user", outputs[1].Column!!.metadata.tableAliasFromModel)
        assertEquals("tu", outputs[1].Column!!.metadata.tableAliasFromAnnotation)
        assertEquals("u", outputs[1].Column!!.metadata.tableAliasFromAst)
        assertNull(outputs[1].Column!!.metadata.columnNameFromModel)
        assertNull(outputs[1].Column!!.metadata.columnNameFromAnnotation)
        assertEquals("plain_name", outputs[1].Column!!.metadata.columnNameFromAst)
        assertEquals("raw_name", outputs[1].metadata.ColumnAlias)
        assertEquals(DataType.STRING, outputs[1].metadata.ExecutionType)
        assertTrue(outputs[1].metadata.IsExecutionTypeExplicit)
        assertNull(outputs[2].Column!!.metadata.resolveTableAlias())
        assertEquals("count(*)", outputs[2].Column!!.metadata.resolveColumnName())
        assertEquals("count(*)", outputs[2].Column!!.metadata.columnNameFromAst)
        assertEquals("row_count", outputs[2].metadata.ColumnAlias)
        assertNull(outputs[2].metadata.PropertyReference)
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("u.user_name"), sql)
        assertTrue(sql.contains("u.plain_name"), sql)
        assertTrue(sql.contains("count(*)"), sql)
        assertTrue(sql.contains("As raw_name"), sql)
        assertTrue(sql.contains("As row_count"), sql)
    }

    @Test
    fun `string source selector resolves a later configured occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column {
                        tableSelector("users")
                        columnSelector("id")
                    }
                }
            }
            .from { table("users").alias("u") }

        val source = query.ast.select!!.columns.single().Column!!
        assertEquals("u", source.metadata.resolveTableAlias())
        assertEquals("id", source.metadata.resolveColumnName())
        assertTrue(MySqlDialect().render(query.ast)!!.contains("u.id"))
    }

    @Test
    fun `changing selected source clears the previous column and property metadata`() {
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column {
                    tableSelector(TestUser::class)
                    columnSelector(TestUser::userName)
                    tableSelector("replacement_source")
                }
            }
        }

        val output = query.ast.select!!.columns.single()
        assertEquals("replacement_source", output.Column!!.metadata.resolveTableAlias())
        assertNull(output.Column!!.metadata.resolveColumnName())
        assertNull(output.Column!!.metadata.columnNameFromModel)
        assertNull(output.Column!!.metadata.columnNameFromAnnotation)
        assertNull(output.Column!!.metadata.columnNameFromAst)
        assertNull(output.Column!!.metadata.columnAliasFromModel)
        assertNull(output.Column!!.metadata.columnAliasFromAnnotation)
        assertNull(output.metadata.PropertyReference)
        assertNull(output.metadata.ExecutionType)
        assertNull(output.metadata.ColumnAlias)
    }

    @Test
    fun `changing selected column preserves the chosen source alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column {
                        tableSelector(TestUser::class)
                        columnSelector(TestUser::userName)
                        columnSelector("replacement_name")
                    }
                }
            }
            .from { model(TestUser::class).alias("u") }

        val output = query.ast.select!!.columns.single()
        assertEquals("u", output.Column!!.metadata.resolveTableAlias())
        assertEquals("replacement_name", output.Column!!.metadata.resolveColumnName())
        assertNull(output.metadata.PropertyReference)
        assertNull(output.metadata.ExecutionType)
    }

    @Test
    fun `replacing model with string table clears its registered alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn { column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) } }
            }
            .from {
                model(TestUser::class).alias("old_source")
                table("replacement_table").alias("replacement_source")
            }

        assertEquals("replacement_source", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("tu", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
    }

    @Test
    fun `replacing model with named CTE clears its registered alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn { column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) } }
            }
            .from {
                model(TestUser::class).alias("old_source")
                cte("replacement_cte").alias("replacement_source")
            }

        assertEquals("replacement_source", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("tu", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
    }

    @Test
    fun `named sources reject ASTs that already contain a derived query`() {
        fun derivedTableAst() = QueryTableAst().apply { derivedQuery = QueryRenderSelectAst() }

        val modelAst = derivedTableAst()
        assertFailsWith<IllegalStateException> {
            QueryTableBuilder(modelAst).model(TestUser::class)
        }
        assertTrue(modelAst.derivedQuery != null)
        assertNull(modelAst.metadata.resolveName())

        val tableAst = derivedTableAst()
        assertFailsWith<IllegalStateException> {
            QueryTableBuilder(tableAst).table("users")
        }
        assertTrue(tableAst.derivedQuery != null)
        assertNull(tableAst.metadata.resolveName())

        val cteAst = derivedTableAst()
        assertFailsWith<IllegalStateException> {
            QueryTableBuilder(cteAst).cte("users_cte")
        }
        assertTrue(cteAst.derivedQuery != null)
        assertNull(cteAst.metadata.resolveName())
    }

    @Test
    fun `model property name is the default output alias when annotation alias is absent`() {
        val query = QueryRenderSelectBuilder().select {
            addColumn { column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::plainName) } }
        }

        val output = query.ast.select!!.columns.single()
        assertEquals("plainName", output.metadata.ColumnAlias)
        assertEquals(TestUser::plainName, output.metadata.PropertyReference)
    }

    @Test
    fun `empty table alias clears model default and typed qualifier`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                }
            }
            .from { model(TestUser::class).alias("") }

        assertEquals("tu", query.ast.table!!.metadata.aliasFromAnnotation)
        assertEquals("", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
    }

    @Test
    fun `explicit select alias overrides default and empty alias clears it`() {
        val overridden = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                alias("name")
            }
        }
        assertEquals("name", overridden.ast.select!!.columns.single().metadata.ColumnAlias)

        val cleared = QueryRenderSelectBuilder().select {
            addColumn {
                alias("")
                column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
            }
        }
        assertNull(cleared.ast.select!!.columns.single().metadata.ColumnAlias)
    }

    @Test
    fun `missing or empty table and column metadata fail`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { model(MissingTableModel::class) }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { model(EmptyTableModel::class) }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column { tableSelector(MissingColumnAnnotationModel::class); columnSelector(MissingColumnAnnotationModel::class, MissingColumnAnnotationModel::value) } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column { tableSelector(EmptyColumnNameModel::class); columnSelector(EmptyColumnNameModel::class, EmptyColumnNameModel::value) } }
            }
        }
    }

    @Test
    fun `string table and column API remains available`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("user_id")
                }
            }
            .from { table("users").alias("u") }

        val column = query.ast.select!!.columns.single()
        assertEquals("users", query.ast.table!!.metadata.nameFromAst)
        assertEquals("u", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("u", column.Column!!.metadata.resolveTableAlias())
        assertEquals("id", column.Column!!.metadata.resolveColumnName())
        assertEquals("user_id", column.metadata.ColumnAlias)
    }

    @Test
    fun `renderer uses effective model alias and explicit select alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                    alias("name")
                }
            }
            .from { model(TestUser::class).alias("u") }

        val sql = MySqlDialect().render(query.ast)!!

        assertTrue(sql.contains("u.user_name"))
        assertTrue(sql.contains("As name"))
        assertTrue(sql.contains("test_users"))
        assertTrue(sql.contains("As u"))
    }

    @Test
    fun `repository model property maps to database column metadata name`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableSelector(Users::class); columnSelector(Users::class, Users::userAge) }
                }
            }
            .from { model(Users::class) }

        val sql = MySqlDialect().render(query.ast)!!

        assertTrue(sql.contains("uu.age"))
        assertFalse(sql.contains("uu.userAge"))
        assertTrue(sql.contains("As u_user_age"))
    }

    @Test
    fun `renderer omits cleared table and select aliases`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableSelector(TestUser::class); columnSelector(TestUser::class, TestUser::userName) }
                    alias("")
                }
            }
            .from { model(TestUser::class).alias("") }

        val sql = MySqlDialect().render(query.ast)!!

        assertTrue(sql.contains("from"))
        assertTrue(sql.contains("test_users"))
        assertTrue(sql.contains("user_name"))
        assertFalse(sql.contains("As "))
        assertFalse(sql.contains("null."))
    }
}
