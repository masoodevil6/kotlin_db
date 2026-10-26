package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.builder.ast.insert_builder.column_insert.QueryColumnInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.column_update.QueryColumnUpdateBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
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
    fun `insert and update column builders keep parameter AST separate from column tags`() {
        val insertBuilder = QueryColumnInsertBuilder()
        insertBuilder.column("email").setValue {
            paramValue("person@example.test")
        }

        assertEquals("email", insertBuilder.ast.metadata.columnTag)
        val insertParameterAst = assertIs<IQueryParameterAst<*>>(insertBuilder.ast.parameterAst)
        assertEquals("email", insertParameterAst.metadata.name)
        assertEquals("person@example.test", insertParameterAst.metadata.value)
        assertEquals(Types.VARCHAR, insertParameterAst.metadata.sqlType)

        val updateBuilder = QueryColumnUpdateBuilder()
        updateBuilder.column("status").setValue { paramValue("active") }

        assertEquals("status", updateBuilder.ast.metadata.columnTag)
        val updateParameterAst = assertIs<IQueryParameterAst<*>>(updateBuilder.ast.parameterAst)
        assertEquals("status", updateParameterAst.metadata.name)
        assertEquals("active", updateParameterAst.metadata.value)
        assertEquals(Types.VARCHAR, updateParameterAst.metadata.sqlType)
    }

    @Test
    fun `scalar condition stores an independent parameter node and preserves binding data`() {
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "age") }
                    operationGreaterThan()
                    sideValueParam {
                        paramName("minimum_age")
                        paramValue(18)
                    }
                }
            }
        }

        val condition = whereCondition(query)
        val parameterNode = assertIs<IQueryParameterAst<*>>(condition.conditionSideRight)

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertTrue(requireNotNull(trackedSql).contains(":minimum_age"), trackedSql)
        assertEquals(1, occurrences.size)
        assertEquals(parameterNode.metadata.name, occurrences.single().name)
        assertEquals(parameterNode.metadata.value, occurrences.single().value)
        assertEquals(parameterNode.metadata.sqlType, occurrences.single().sqlType)
        assertIs<QueryParameterBinding>(occurrences.single())

        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, dialect),
        )
        val renderedBinding = rendered.parameterOccurrences.single()
        assertEquals(parameterNode.metadata.name, renderedBinding.name)
        assertEquals(parameterNode.metadata.value, renderedBinding.value)
        assertEquals(parameterNode.metadata.sqlType, renderedBinding.sqlType)
        assertIs<QueryParameterBinding>(renderedBinding)
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

                    sideValueCollection {
                        addParam {
                            paramName("userId1")
                            paramValue(11)
                        }
                        addParam {
                            paramName("userId2")
                            paramValue(22)
                        }
                    }
                }
            }
        }

        val collection = whereCondition(query).conditionSideRight as IQueryConditionsCollectionAst
        val parameters = collection.paramsCollection.map { assertIs<IQueryParameterAst<*>>(it) }
        assertEquals(listOf("userId1", "userId2"), parameters.map { it.metadata.name })
        assertEquals(listOf(11, 22), parameters.map { it.metadata.value })

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertEquals(listOf("userId1", "userId2"), placeholderNames(requireNotNull(trackedSql)))
        assertEquals(parameters.size, occurrences.size)
        parameters.indices.forEach { index ->
            assertEquals(parameters[index].metadata.name, occurrences[index].name)
            assertEquals(parameters[index].metadata.value, occurrences[index].value)
            assertEquals(parameters[index].metadata.sqlType, occurrences[index].sqlType)
            assertIs<QueryParameterBinding>(occurrences[index])
        }
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
                        sideValueParam {
                            paramName("owner_id")
                            paramValue(7)
                        }
                    }
                }
            }
        }

        val joinCondition = query.ast.joins!!.joins.single().joinConditions!!.conditions.single() as IQueryConditionsAst
        val parameter = assertIs<IQueryParameterAst<*>>(joinCondition.conditionSideRight)
        assertEquals("owner_id", parameter.metadata.name)
        assertEquals(7, parameter.metadata.value)

        val dialect = MySqlDialect()
        val ordinarySql = dialect.render(query.ast)
        val (trackedSql, occurrences) = dialect.renderWithBindings(query.ast)

        assertEquals(ordinarySql, trackedSql)
        assertTrue(requireNotNull(trackedSql).contains(":owner_id"), trackedSql)
        assertEquals(1, occurrences.size)
        val occurrence = assertIs<IQueryParameterBinding>(occurrences.single())
        assertEquals(parameter.metadata.name, occurrence.name)
        assertEquals(parameter.metadata.value, occurrence.value)
        assertIs<QueryParameterBinding>(occurrence)
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
                    sideValueParam {
                        paramName("unused")
                        paramValue(33)
                    }
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
                            sideValueParam {
                                paramName("shared")
                                paramValue(11)
                            }
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
                    sideValueParam {
                        paramName("shared")
                        paramValue(11)
                    }
                }
            }
        }

        val nestedCondition = query.ast.table!!.derivedQuery!!.where!!.condition!!.conditions.single() as IQueryConditionsAst
        val ownerCondition = whereCondition(query)
        assertSame(nestedCondition.conditionSideRight, ownerCondition.conditionSideRight)
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(listOf("shared", "shared"), rendered.parameterOccurrences.map { it.name })
        assertEquals(listOf(11, 11), rendered.parameterOccurrences.map { it.value })
        assertSame(rendered.parameterOccurrences[0], rendered.parameterOccurrences[1])
    }

    private fun whereCondition(query: QueryRenderSelectBuilder): IQueryConditionsAst =
        query.ast.where!!.condition!!.conditions.single() as IQueryConditionsAst

    private fun placeholderNames(sql: String): List<String> =
        Regex(":([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(sql)
            .map { it.groupValues[1] }
            .toList()
}
