package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.builder.ast.insert_builder.column_insert.QueryColumnInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.column_update.QueryColumnUpdateBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParameterIdentityPhaseOneTest {

    @Test
    fun `insert and update column builders retain typed parameter references beside legacy tags`() {
        val insertBuilder = QueryColumnInsertBuilder()
        insertBuilder.column("email", "person@example.test")

        assertEquals("email", insertBuilder.ast.columnTag)
        assertSame(insertBuilder.params.single(), insertBuilder.ast.parameter)
        assertEquals("person@example.test", insertBuilder.ast.parameter?.value)

        val updateBuilder = QueryColumnUpdateBuilder()
        updateBuilder.column("status", "active")

        assertEquals("status", updateBuilder.ast.columnTag)
        assertSame(updateBuilder.params.single(), updateBuilder.ast.parameter)
        assertEquals("active", updateBuilder.ast.parameter?.value)
    }

    @Test
    fun `scalar condition stores and renders the original parameter reference`() {
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "age") }
                    operationGreaterThan()
                    sideValue("minimum_age", 18)
                }
            }
        }

        val condition = whereCondition(query)
        val parameter = assertIs<SqlParameter<*>>(condition.conditionSideRight)
        assertSame(query.params.single(), parameter)

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertTrue(requireNotNull(trackedSql).contains(":minimum_age"), trackedSql)
        assertEquals(1, occurrences.size)
        assertSame(parameter, occurrences.single())
    }

    @Test
    fun `collection condition preserves references values and placeholder order`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationIn()
                    sideValueCollection("user_id") {
                        addParam(11)
                        addParam(22)
                    }
                }
            }
        }

        val collection = whereCondition(query).conditionSideRight as IQueryConditionsCollectionAst
        val parameters = collection.paramsCollection.map { assertIs<SqlParameter<*>>(it) }
        assertEquals(listOf("user_id0", "user_id1"), parameters.map { it.name })
        assertEquals(listOf(11, 22), parameters.map { it.value })
        assertEquals(2, query.params.size)
        parameters.indices.forEach { assertSame(query.params[it], parameters[it]) }

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertEquals(listOf("user_id0", "user_id1"), placeholderNames(requireNotNull(trackedSql)))
        assertEquals(parameters.size, occurrences.size)
        parameters.indices.forEach { assertSame(parameters[it], occurrences[it]) }
    }

    @Test
    fun `join conditions preserve and render their parameter references`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.joins {
            addJoin {
                innerJoin()
                table { table("details").alias("d") }
                condition {
                    addCondition {
                        logicalOn()
                        sideSelector { tableColumn("d", "owner_id") }
                        operationEqual()
                        sideValue("owner_id", 7)
                    }
                }
            }
        }

        val joinCondition = query.ast.joins!!.joins.single().joinConditions!!.conditions.single() as IQueryConditionsAst
        val parameter = assertIs<SqlParameter<*>>(joinCondition.conditionSideRight)
        assertSame(query.params.single(), parameter)

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertTrue(requireNotNull(trackedSql).contains(":owner_id"), trackedSql)
        assertEquals(1, occurrences.size)
        assertSame(parameter, occurrences.single())
    }

    @Test
    fun `omitted condition does not leave an orphan occurrence`() {
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryColumnsBaseAst::class,
            object : IAstRenderer<IQueryColumnsBaseAst, QueryColumnBaseData?> {
                override fun render(
                    ast: IQueryColumnsBaseAst,
                    ctx: IRenderContext,
                    dataClass: QueryColumnBaseData?,
                ): String? = null
            },
        )
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValue("unused", 33)
                }
            }
        }

        val (sql, occurrences) = dialect.renderWithBindings(query.ast)

        assertFalse(requireNotNull(sql).contains(":unused"), sql)
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `nested table query and owner where clause share one binding scope`() {
        val query = QueryRenderSelectBuilder()
        query.from {
            table {
                select { addColumn { column { tableAttribute("1") }; alias("value") } }
                from { table("inner_table") }
                where {
                    conditions {
                        addCondition {
                            sideSelector { tableAttribute("1") }
                            operationEqual()
                            sideValue("shared", 11)
                        }
                    }
                }
            }.alias("nested")
        }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValue("shared", 11)
                }
            }
        }

        val nestedCondition = query.ast.table!!.derivedQuery!!.where!!.condition!!.conditions.single() as IQueryConditionsAst
        val ownerCondition = whereCondition(query)
        assertSame(nestedCondition.conditionSideRight, ownerCondition.conditionSideRight)
        assertEquals(2, query.params.size)
        assertSame(query.params[0], query.params[1])
    }

    private fun whereCondition(query: QueryRenderSelectBuilder): IQueryConditionsAst =
        query.ast.where!!.condition!!.conditions.single() as IQueryConditionsAst

    private fun placeholderNames(sql: String): List<String> =
        Regex(":([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(sql)
            .map { it.groupValues[1] }
            .toList()
}
