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
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.query_render_update.QueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.insert_data.query_render_insert.QueryRenderInsertData
import gog.my_project.data_base.query.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentityAwareDmlRenderingTest {
    @Test
    fun `UPDATE setValue AST definitions render assignments before WHERE in occurrence order`() {
        val update = QueryRenderUpdateBuilder()
            .table { table("records") }
            .addValue {
                column("first_value")
                setValue {
                    paramName("first_update_parameter")
                    paramValue(11)
                }
            }
            .addValue {
                column("r", "second_value")
                setValue { paramValue("22") }
            }
            .where {
                conditions {
                    addCondition {
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName("where_parameter")
                            paramValue(33)
                        }
                    }
                }
            }

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.renderDml(update.ast, MySqlDialect()),
        )
        val normalizedDisplaySql = result.displaySql.replace(Regex("\\s+"), " ")

        assertTrue(normalizedDisplaySql.contains("first_value = :first_update_parameter"), result.displaySql)
        assertTrue(normalizedDisplaySql.contains("r.second_value = :r_second_value"), result.displaySql)
        assertTrue(normalizedDisplaySql.contains("1 = :where_parameter"), result.displaySql)
        assertEquals(listOf(11, "22", 33), result.parameterOccurrences.map { it.value })
        assertEquals(
            listOf("first_update_parameter", "r_second_value", "where_parameter"),
            result.parameterOccurrences.map { it.name },
        )
        assertEquals(listOf(Types.INTEGER, Types.VARCHAR, Types.INTEGER), result.parameterOccurrences.map { it.sqlType })
        assertEquals(3, result.jdbcSql.count { it == '?' })
        val expectedJdbcSql = result.displaySql
            .replace(":first_update_parameter", "?")
            .replace(":r_second_value", "?")
            .replace(":where_parameter", "?")
        assertEquals(expectedJdbcSql, result.jdbcSql)
    }

    @Test
    fun `reused AST parameter produces matching named and positional occurrences`() {
        val update = QueryRenderUpdateBuilder()
            .table { table("records") }
            .addValue {
                column("first_value")
                setValue {
                    paramName("shared_parameter")
                    paramValue(71)
                }
            }
            .addValue {
                column("second_value")
                setValue {
                    paramName("unused_parameter")
                    paramValue(72)
                }
            }
        val sharedAst = requireNotNull(update.ast.columns[0].parameterAst)
        update.ast.columns[1].parameterAst = sharedAst

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.renderDml(update.ast, MySqlDialect()),
        )

        assertEquals(listOf("shared_parameter", "shared_parameter"), result.parameterOccurrences.map { it.name })
        assertEquals(listOf(71, 71), result.parameterOccurrences.map { it.value })
        assertSame(result.parameterOccurrences[0], result.parameterOccurrences[1])
        assertEquals(2, Regex(":shared_parameter\\b").findAll(result.displaySql).count())
        assertEquals(2, result.jdbcSql.count { it == '?' })
        assertEquals(result.displaySql.replace(":shared_parameter", "?"), result.jdbcSql)
    }

    @Test
    fun `INSERT setValue AST definitions render in column occurrence order`() {
        val insert = QueryRenderInsertBuilder()
            .table { table("records") }
            .addValue {
                column("first_value")
                setValue {
                    paramName("first_insert_parameter")
                    paramValue(11)
                }
            }
            .addValue {
                column("second_value")
                setValue {
                    paramName("second_insert_parameter")
                    paramValue("22")
                }
            }

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.renderDml(insert.ast, MySqlDialect()),
        )

        assertTrue(result.displaySql.contains(":first_insert_parameter"), result.displaySql)
        assertTrue(result.displaySql.contains(":second_insert_parameter"), result.displaySql)
        assertEquals(listOf(11, "22"), result.parameterOccurrences.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.VARCHAR), result.parameterOccurrences.map { it.sqlType })
        assertEquals(2, result.jdbcSql.count { it == '?' })
    }

    @Test
    fun `repeated reference is recorded once for each emitted DML placeholder`() {
        val shared = parameterAst("shared_value", 77, Types.INTEGER)
        val insert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { metadata.nameFromAst = "records" }
            columns += QueryColumnInsertAst().apply {
                metadata.columnName = "first_value"
                metadata.columnTag = shared.metadata.name
                parameterAst = shared
            }
            columns += QueryColumnInsertAst().apply {
                metadata.columnName = "second_value"
                metadata.columnTag = shared.metadata.name
                parameterAst = shared
            }
        }

        val dialect = MySqlDialect()
        val insertResult = assertIs<IdentityAwareQueryRenderResult>(QueryParameterRendering.renderDml(insert, dialect))
        val insertOccurrences = insertResult.parameterOccurrences

        assertEquals(2, insertOccurrences.size)
        assertEquals(listOf("shared_value", "shared_value"), insertOccurrences.map { it.name })
        assertEquals(listOf(77, 77), insertOccurrences.map { it.value })
        assertSame(insertOccurrences[0], insertOccurrences[1])
    }

    @Test
    fun `AST insert update and legacy delete bindings retain SQL order for MySQL and MariaDB`() {
        val dialects = listOf(MySqlDialect(), MariaDbDialect())

        for (dialect in dialects) {
            val insertFirst = parameterAst("shared_value", 11, Types.INTEGER)
            val insertSecond = parameterAst("shared_value", "22", Types.VARCHAR)
            val insert = QueryRenderInsertAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
                columns += QueryColumnInsertAst().apply {
                    metadata.columnName = "first_value"
                    metadata.columnTag = "shared_value"
                    parameterAst = insertFirst
                }
                columns += QueryColumnInsertAst().apply {
                    metadata.columnName = "second_value"
                    metadata.columnTag = "shared_value"
                    parameterAst = insertSecond
                }
            }
            val insertResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(insert, dialect),
            )
            val insertSql = insertResult.displaySql
            val insertOccurrences = insertResult.parameterOccurrences
            assertEquals(dialect.render(insert), insertSql)
            assertEquals(listOf(11, "22"), insertOccurrences.map { it.value })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR), insertOccurrences.map { it.sqlType })
            assertEquals(listOf("shared_value", "shared_value"), insertOccurrences.map { it.name })

            val updateFirst = parameterAst("shared_value", 33, Types.INTEGER)
            val updateSecond = parameterAst("shared_value", "44", Types.VARCHAR)
            val whereParameter = parameterAst("shared_value", null, Types.INTEGER)
            val update = QueryRenderUpdateAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
                columns += QueryColumnUpdateAst().apply {
                    metadata.columnAlias = "r"
                    metadata.columnName = "first_value"
                    metadata.columnTag = "shared_value"
                    parameterAst = updateFirst
                }
                columns += QueryColumnUpdateAst().apply {
                    metadata.columnAlias = "r"
                    metadata.columnName = "second_value"
                    metadata.columnTag = "shared_value"
                    parameterAst = updateSecond
                }
                where = where(whereParameter)
            }
            val updateResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(update, dialect),
            )
            val updateSql = updateResult.displaySql
            val updateOccurrences = updateResult.parameterOccurrences
            assertEquals(dialect.render(update), updateSql)
            assertEquals(listOf(33, "44", null), updateOccurrences.map { it.value })
            assertEquals(listOf("shared_value", "shared_value", "shared_value"), updateOccurrences.map { it.name })
            assertEquals(null, updateOccurrences[2].value)
            assertEquals(Types.INTEGER, updateOccurrences[2].sqlType)

            val deleteParameter = SqlParameter("shared_value", 55, Types.INTEGER)
            val delete = QueryRenderDeleteAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
                where = where(deleteParameter)
            }
            val deleteResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.renderDml(delete, dialect),
            )
            val deleteSql = deleteResult.displaySql
            val deleteOccurrences = deleteResult.parameterOccurrences
            assertEquals(dialect.render(delete), deleteSql)
            assertEquals(listOf(55), deleteOccurrences.map { it.value })
            assertSame(deleteParameter, deleteOccurrences.single())
        }
    }

    @Test
    fun `INSERT and UPDATE require AST parameters and valid parameter metadata`() {
        val dialect = MySqlDialect()
        val legacyInsert = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { metadata.nameFromAst = "records" }
            columns += QueryColumnInsertAst().apply {
                metadata.columnName = "value"
                metadata.columnTag = "legacy_value"
            }
        }

        val insertFailure = assertFailsWith<IllegalStateException> {
            QueryParameterRendering.renderDml(legacyInsert, dialect)
        }
        assertTrue(insertFailure.message.orEmpty().contains("INSERT column"), insertFailure.message)

        val legacyUpdate = QueryRenderUpdateAst().apply {
            table = QueryTableAst().apply { metadata.nameFromAst = "records" }
            columns += QueryColumnUpdateAst().apply {
                metadata.columnAlias = "r"
                metadata.columnName = "value"
                metadata.columnTag = "value"
            }
        }
        val updateFailure = assertFailsWith<IllegalStateException> {
            QueryParameterRendering.renderDml(legacyUpdate, dialect)
        }
        assertTrue(updateFailure.message.orEmpty().contains("UPDATE column"), updateFailure.message)

        val invalidParameter = QueryRenderInsertAst().apply {
            table = QueryTableAst().apply { metadata.nameFromAst = "records" }
            columns += QueryColumnInsertAst().apply {
                metadata.columnName = "value"
                metadata.columnTag = "value"
                parameterAst = QueryParameterAst<Any?>().apply {
                    metadata.name = "value_without_type"
                    metadata.value = 1
                }
            }
        }
        val metadataFailure = assertFailsWith<IllegalArgumentException> {
            QueryParameterRendering.renderDml(invalidParameter, dialect)
        }
        assertTrue(metadataFailure.message.orEmpty().contains("SQL type"), metadataFailure.message)
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
        val insert = QueryRenderInsertAst().apply {
                table = QueryTableAst().apply { metadata.nameFromAst = "records" }
            columns += QueryColumnInsertAst().apply {
                metadata.columnName = "value"
                metadata.columnTag = "value"
                parameterAst = parameterAst("value", 1, Types.INTEGER)
            }
        }

        assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.renderDml(insert, dialect)
        }
    }

    private fun where(parameter: Any): QueryWhereAst = QueryWhereAst().apply {
        condition = QueryConditionsGroupsAst().apply {
            conditions += QueryConditionsAst().apply {
                conditionSideLeft = QueryColumnsBaseAst().apply { metadata.columnNameFromAst = "1" }
                metadata.conditionOperation = "="
                conditionSideRight = parameter
            }
        }
    }

    private fun parameterAst(name: String, value: Any?, sqlType: Int) = QueryParameterAst<Any?>().apply {
        metadata.name = name
        metadata.value = value
        metadata.sqlType = sqlType
    }
}
