package gog.my_project.data_base.query.renderer.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.query_render_update.QueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.insert_data.query_render_insert.QueryRenderInsertData
import gog.my_project.data_base.query.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class IdentityAwareDmlRenderingTest {
    @Test
    fun `repeated reference is recorded once for each emitted DML placeholder`() {
        val shared = SqlParameter("shared_value", 77, Types.INTEGER)
        val insert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { this.table = "records" }
            columns += QueryColumnInsertAst().apply {
                columnName = "first_value"
                columnTag = shared.name
                parameter = shared
            }
            columns += QueryColumnInsertAst().apply {
                columnName = "second_value"
                columnTag = shared.name
                parameter = shared
            }
        }

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.renderDml(insert, MySqlDialect()),
        )

        assertEquals(2, result.parameterOccurrences.size)
        assertSame(shared, result.parameterOccurrences[0])
        assertSame(shared, result.parameterOccurrences[1])
        assertEquals(2, result.jdbcSql.count { it == '?' })
    }

    @Test
    fun `insert update and delete retain typed occurrences in SQL order for MySQL and MariaDB`() {
        val dialects = listOf(MySqlDialect(), MariaDbDialect())

        for (dialect in dialects) {
            val insertFirst = SqlParameter("shared_value", 11, Types.INTEGER)
            val insertSecond = SqlParameter("shared_value", "22", Types.VARCHAR)
            val insert = QueryRenderInsertAst().apply {
                table = QueryTableAst().apply { this.table = "records" }
                columns += QueryColumnInsertAst().apply {
                    columnName = "first_value"
                    columnTag = "shared_value"
                    parameter = insertFirst
                }
                columns += QueryColumnInsertAst().apply {
                    columnName = "second_value"
                    columnTag = "shared_value"
                    parameter = insertSecond
                }
            }
            val insertResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(insert, dialect),
            )
            assertEquals(dialect.render(insert), insertResult.displaySql)
            assertEquals(listOf(11, "22"), insertResult.parameterOccurrences.map { it.value })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR), insertResult.parameterOccurrences.map { it.sqlType })
            assertSame(insertFirst, insertResult.parameterOccurrences[0])
            assertSame(insertSecond, insertResult.parameterOccurrences[1])
            assertEquals(2, insertResult.jdbcSql.count { it == '?' })
            assertEquals(insertResult.displaySql.replace(":shared_value", "?"), insertResult.jdbcSql)

            val updateFirst = SqlParameter("shared_value", 33, Types.INTEGER)
            val updateSecond = SqlParameter("shared_value", "44", Types.VARCHAR)
            val whereParameter = SqlParameter.nullOf("shared_value", Types.INTEGER)
            val update = QueryRenderUpdateAst().apply {
                table = QueryTableAst().apply { this.table = "records" }
                columns += QueryColumnUpdateAst().apply {
                    columnAlias = "r"
                    columnName = "first_value"
                    columnTag = "shared_value"
                    parameter = updateFirst
                }
                columns += QueryColumnUpdateAst().apply {
                    columnAlias = "r"
                    columnName = "second_value"
                    columnTag = "shared_value"
                    parameter = updateSecond
                }
                where = where(whereParameter)
            }
            val updateResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(update, dialect),
            )
            assertEquals(dialect.render(update), updateResult.displaySql)
            assertEquals(listOf(33, "44", null), updateResult.parameterOccurrences.map { it.value })
            assertEquals(3, updateResult.jdbcSql.count { it == '?' })
            assertEquals(updateResult.displaySql.replace(":shared_value", "?"), updateResult.jdbcSql)
            assertSame(updateFirst, updateResult.parameterOccurrences[0])
            assertSame(updateSecond, updateResult.parameterOccurrences[1])
            assertSame(whereParameter, updateResult.parameterOccurrences[2])

            val deleteParameter = SqlParameter("shared_value", 55, Types.INTEGER)
            val delete = QueryRenderDeleteAst().apply {
                table = QueryTableAst().apply { this.table = "records" }
                where = where(deleteParameter)
            }
            val deleteResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(delete, dialect),
            )
            assertEquals(dialect.render(delete), deleteResult.displaySql)
            assertEquals(listOf(55), deleteResult.parameterOccurrences.map { it.value })
            assertSame(deleteParameter, deleteResult.parameterOccurrences.single())
            assertEquals(1, deleteResult.jdbcSql.count { it == '?' })
            assertEquals(deleteResult.displaySql.replace(":shared_value", "?"), deleteResult.jdbcSql)
        }
    }

    @Test
    fun `legacy DML tags keep named rendering and typed plus legacy slots fail fast`() {
        val dialect = MySqlDialect()
        val legacyInsert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { this.table = "records" }
            columns += QueryColumnInsertAst().apply {
                columnName = "value"
                columnTag = "legacy_value"
            }
        }

        val legacyResult = assertIs<LegacyQueryRenderResult>(
            QueryParameterRendering.renderDml(legacyInsert, dialect),
        )
        assertEquals(dialect.render(legacyInsert), legacyResult.sql)
        assertEquals(true, legacyResult.sql.orEmpty().contains(":legacy_value"))

        val mixedInsert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { this.table = "records" }
            columns += QueryColumnInsertAst().apply {
                columnName = "typed_value"
                columnTag = "typed_value"
                parameter = SqlParameter.of("typed_value", 1)
            }
            columns += QueryColumnInsertAst().apply {
                columnName = "legacy_value"
                columnTag = "legacy_value"
            }
        }
        assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.renderDml(mixedInsert, dialect)
        }
    }

    @Test
    fun `unverified DML root renderer is rejected for identity-aware rendering`() {
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryRenderInsertAst::class,
            object : IAstRenderer<IQueryRenderInsertAst, QueryRenderInsertData?> {
                override fun render(
                    ast: IQueryRenderInsertAst,
                    ctx: IRenderContext,
                    dataClass: QueryRenderInsertData?,
                ): String = "insert into records values (:value)"
            },
        )
        val parameter = SqlParameter.of("value", 1)
        val insert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { this.table = "records" }
            columns += QueryColumnInsertAst().apply {
                columnName = "value"
                columnTag = "value"
                this.parameter = parameter
            }
        }

        assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.renderDml(insert, dialect)
        }
    }

    private fun where(parameter: SqlParameter<*>): QueryWhereAst = QueryWhereAst().apply {
        condition = QueryConditionsGroupsAst().apply {
            conditions += QueryConditionsAst().apply {
                conditionSideLeft = QueryColumnsBaseAst().apply { tableAttribute = "1" }
                conditionOperation = "="
                conditionSideRight = parameter
            }
        }
    }
}
