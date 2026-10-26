package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class ParameterDslTest {

    @Test
    fun `sideValueParam stores the definition directly in the AST`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValueParam {
                        paramName("id1")
                        paramValue(1)
                    }
                }
            }
        }

        val parameter = assertIs<IQueryParameterAst<*>>(query.condition().conditionSideRight)
        assertEquals("id1", parameter.metadata.name)
        assertEquals(1, parameter.metadata.value)
    }

    @Test
    fun `parameter DSL stores the definition in AST and rendering supplies the binding adapter`() {
        val query = queryWithCondition {
            sideValueParam {
                paramName("id1")
                paramValue(1)
            }
        }

        val condition = query.condition()
        val parameterNode = assertIs<IQueryParameterAst<*>>(condition.conditionSideRight)
        assertEquals("id1", parameterNode.metadata.name)
        assertEquals(1, parameterNode.metadata.value)
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertContains(rendered.displaySql, ":id1")
        assertEquals(1, rendered.parameterOccurrences.size)
        val binding = rendered.parameterOccurrences.single()
        assertEquals(parameterNode.metadata.name, binding.name)
        assertEquals(parameterNode.metadata.value, binding.value)
        assertEquals(parameterNode.metadata.sqlType, binding.sqlType)
        assertIs<QueryParameterBinding>(binding)
    }

    @Test
    fun `parameter DSL creates a deterministic default name when name is omitted`() {
        val query = queryWithCondition {
            sideValueParam {
                paramValue(null)
            }
        }

        val condition = query.condition()
        val parameterNode = assertIs<IQueryParameterAst<*>>(condition.conditionSideRight)
        assertEquals("param0", parameterNode.metadata.name)
        assertEquals(null, parameterNode.metadata.value)
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        val binding = rendered.parameterOccurrences.single()
        assertEquals(parameterNode.metadata.name, binding.name)
        assertEquals(parameterNode.metadata.value, binding.value)
        assertEquals(parameterNode.metadata.sqlType, binding.sqlType)
        assertEquals(binding, rendered.parameterOccurrences.single())
    }

    @Test
    fun `collection DSL stores independent parameter AST nodes in emitted order`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationIn()
                    sideValueCollection {
                        addParam {
                            paramName("id1")
                            paramValue(1)
                        }
                        addParam {
                            paramName("id2")
                            paramValue(2)
                        }
                    }
                }
            }
        }

        val collection = assertIs<IQueryConditionsCollectionAst>(query.condition().conditionSideRight)
        val parameters = collection.paramsCollection.map { assertIs<IQueryParameterAst<*>>(it) }
        assertEquals(listOf("id1", "id2"), parameters.map { it.metadata.name })
        assertEquals(listOf(1, 2), parameters.map { it.metadata.value })
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertContains(rendered.displaySql, ":id1")
        assertContains(rendered.displaySql, ":id2")
        assertEquals(listOf("id1", "id2"), rendered.parameterOccurrences.map { it.name })
        assertEquals(listOf(1, 2), rendered.parameterOccurrences.map { it.value })
        parameters.indices.forEach { index ->
            assertEquals(parameters[index].metadata.name, rendered.parameterOccurrences[index].name)
            assertEquals(parameters[index].metadata.value, rendered.parameterOccurrences[index].value)
            assertEquals(parameters[index].metadata.sqlType, rendered.parameterOccurrences[index].sqlType)
            assertIs<QueryParameterBinding>(rendered.parameterOccurrences[index])
        }
    }

    @Test
    fun `collection DSL preserves independent same-name parameter node identities`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationIn()
                    sideValueCollection {
                        addParam {
                            paramName("shared")
                            paramValue(11)
                        }
                        addParam {
                            paramName("shared")
                            paramValue(22)
                        }
                    }
                }
            }
        }

        val collection = assertIs<IQueryConditionsCollectionAst>(query.condition().conditionSideRight)
        val parameters = collection.paramsCollection.map { assertIs<IQueryParameterAst<*>>(it) }
        assertNotSame(parameters[0], parameters[1])
        assertEquals(listOf("shared", "shared"), parameters.map { it.metadata.name })
        assertEquals(listOf(11, 22), parameters.map { it.metadata.value })

        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(listOf("shared", "shared"), rendered.parameterOccurrences.map { it.name })
        assertEquals(listOf(11, 22), rendered.parameterOccurrences.map { it.value })
        assertIs<QueryParameterBinding>(rendered.parameterOccurrences[0])
        assertIs<QueryParameterBinding>(rendered.parameterOccurrences[1])
        assertNotSame(rendered.parameterOccurrences[0], rendered.parameterOccurrences[1])
    }

    @Test
    fun `parameter DSL rejects a missing value`() {
        assertFailsWith<IllegalStateException> {
            queryWithCondition {
                sideValueParam {
                    paramName("missing")
                }
            }
        }
    }

    @Test
    fun `parameter DSL rejects a second value`() {
        assertFailsWith<IllegalStateException> {
            queryWithCondition {
                sideValueParam {
                    paramValue(1)
                    paramValue(2)
                }
            }
        }
    }

    @Test
    fun `parameter DSL rejects an invalid explicit name`() {
        assertFailsWith<IllegalArgumentException> {
            queryWithCondition {
                sideValueParam {
                    paramName("bad-name")
                    paramValue(1)
                }
            }
        }
    }

    @Test
    fun `sideValueColumn stores a column AST in the right side`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValueColumn { tableColumn("u", "owner_id") }
                }
            }
        }

        val right = assertIs<IQueryColumnsBaseAst>(query.condition().conditionSideRight)
        assertEquals("u", right.metadata.resolveTableAlias())
        assertEquals("owner_id", right.metadata.resolveColumnName())
    }

    private fun queryWithCondition(
        blockCondition: IQueryConditionsApi.() -> Unit,
    ): QueryRenderSelectBuilder {
        val query = QueryRenderSelectBuilder()
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    blockCondition()
                }
            }
        }
        return query
    }

    private fun QueryRenderSelectBuilder.condition(): IQueryConditionsAst =
        ast.where!!.condition!!.conditions.single() as IQueryConditionsAst
}
