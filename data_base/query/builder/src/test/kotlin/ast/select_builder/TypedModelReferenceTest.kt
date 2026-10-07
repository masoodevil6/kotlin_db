package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.models.eloquent.modules.users.Users
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
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

    val unannotated: String? = null
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

class TypedModelReferenceTest {

    @Test
    fun `source alias is independent of table and cte declaration order`() {
        val tableQuery = QueryRenderSelectBuilder().table {
            alias("shared")
            table("users")
        }
        val cteQuery = QueryRenderSelectBuilder().table {
            cte("user_cte")
            alias("shared")
        }

        assertEquals("shared", tableQuery.ast.table!!.tableAlias)
        assertEquals("shared", tableQuery.ast.table!!.cteAlias)
        assertEquals("shared", cteQuery.ast.table!!.tableAlias)
        assertEquals("shared", cteQuery.ast.table!!.cteAlias)
    }

    @Test
    fun `string source can be declared without an alias`() {
        val query = QueryRenderSelectBuilder().table {
            table("users")
        }

        assertEquals("", query.ast.table!!.tableAlias)
    }

    @Test
    fun `typed reference resolves metadata and applies later table alias override`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column(TestUser::class, TestUser::userName)
                }
            }
            .table {
                table(TestUser::class).alias("u")
            }

        val table = query.ast.table!!
        val column = query.ast.select!!.columns.single()

        assertEquals("test_users", table.table)
        assertEquals("u", table.tableAlias)
        assertEquals("u", column.Column!!.tableAlias)
        assertEquals("user_name", column.Column!!.column)
        assertEquals("user_name_default", column.ColumnAlias)
    }

    @Test
    fun `typed column uses table and select defaults`() {
        val query = QueryRenderSelectBuilder()
            .table { table(TestUser::class) }
            .select {
                addColumn {
                    column(TestUser::class, TestUser::userName)
                }
            }

        val column = query.ast.select!!.columns.single()
        assertEquals("tu", query.ast.table!!.tableAlias)
        assertEquals("tu", column.Column!!.tableAlias)
        assertEquals("user_name_default", column.ColumnAlias)
    }

    @Test
    fun `empty table alias clears model default and typed qualifier`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column(TestUser::class, TestUser::userName)
                }
            }
            .table { table(TestUser::class).alias("") }

        assertEquals("", query.ast.table!!.tableAlias)
        assertEquals("", query.ast.select!!.columns.single().Column!!.tableAlias)
    }

    @Test
    fun `explicit select alias overrides default and empty alias clears it`() {
        val overridden = QueryRenderSelectBuilder().select {
            addColumn {
                column(TestUser::class, TestUser::userName)
                alias("name")
            }
        }
        assertEquals("name", overridden.ast.select!!.columns.single().ColumnAlias)

        val cleared = QueryRenderSelectBuilder().select {
            addColumn {
                alias("")
                column(TestUser::class, TestUser::userName)
            }
        }
        assertNull(cleared.ast.select!!.columns.single().ColumnAlias)
    }

    @Test
    fun `missing or empty table and column metadata fail`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().table { table(MissingTableModel::class) }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().table { table(EmptyTableModel::class) }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column(MissingColumnAnnotationModel::class, MissingColumnAnnotationModel::value) }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().select {
                addColumn { column(EmptyColumnNameModel::class, EmptyColumnNameModel::value) }
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
            .table { table("users").alias("u") }

        val column = query.ast.select!!.columns.single()
        assertEquals("users", query.ast.table!!.table)
        assertEquals("u", query.ast.table!!.tableAlias)
        assertEquals("u", column.Column!!.tableAlias)
        assertEquals("id", column.Column!!.column)
        assertEquals("user_id", column.ColumnAlias)
    }

    @Test
    fun `renderer uses effective model alias and explicit select alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column(TestUser::class, TestUser::userName)
                    alias("name")
                }
            }
            .table { table(TestUser::class).alias("u") }

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
                    column(Users::class, Users::userAge)
                }
            }
            .table { table(Users::class) }

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
                    column(TestUser::class, TestUser::userName)
                    alias("")
                }
            }
            .table { table(TestUser::class).alias("") }

        val sql = MySqlDialect().render(query.ast)!!

        assertTrue(sql.contains("from"))
        assertTrue(sql.contains("test_users"))
        assertTrue(sql.contains("user_name"))
        assertFalse(sql.contains("As "))
        assertFalse(sql.contains("null."))
    }
}
