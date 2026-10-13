package gog.my_project.data_base.query.meta_data.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.conditions.IQueryIsConditionAst
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection.QueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.query_render_update.QueryRenderUpdateAst
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QueryParameterMetadataTest {
    @Test
    fun `same parameter reference in scalar and collection slots is one definition`() {
        val parameter = SqlParameter.of("shared", 11)
        val ast = QueryRenderSelectAst().apply {
            where = whereOf(
                conditionWith(parameter),
                conditionWith(QueryConditionsCollectionAst().apply {
                    paramsCollection += parameter
                    paramsCollection += parameter
                }),
            )
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        assertTrue(metadata.containsDefinition(parameter))
        assertEquals(
            listOf(parameter, parameter),
            metadata.validateOccurrences(listOf(parameter, parameter)),
        )
    }

    @Test
    fun `equal but distinct same-name parameters remain separate definitions`() {
        val first = SqlParameter.of("same", 11)
        val equalButDistinct = first.copy()
        assertEquals(first, equalButDistinct)
        assertNotSame(first, equalButDistinct)

        val ast = QueryRenderSelectAst().apply {
            where = whereOf(conditionWith(first), conditionWith(equalButDistinct))
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        assertTrue(metadata.containsDefinition(first))
        assertTrue(metadata.containsDefinition(equalButDistinct))
        assertFailsWith<IllegalArgumentException> {
            metadata.validateOccurrences(listOf(first, SqlParameter.of("same", 11)))
        }
    }

    @Test
    fun `same-name parameters with different values or SQL types are independent`() {
        val differentValue = SqlParameter.of("shared_value", 22)
        val differentType = SqlParameter("shared_value", "22", Types.VARCHAR)
        val ast = QueryRenderSelectAst().apply {
            where = whereOf(
                conditionWith(SqlParameter.of("shared_value", 11)),
                conditionWith(differentValue),
                conditionWith(differentType),
            )
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        assertTrue(metadata.containsDefinition(differentValue))
        assertTrue(metadata.containsDefinition(differentType))
    }

    @Test
    fun `nullable and unused AST definitions are allowed`() {
        val nullable = SqlParameter.nullOf("nullable", Types.INTEGER)
        val unused = SqlParameter.of("unused", 2)
        val ast = QueryRenderSelectAst().apply {
            where = whereOf(conditionWith(nullable), conditionWith(unused))
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        assertTrue(metadata.containsDefinition(nullable))
        assertTrue(metadata.containsDefinition(unused))
        assertEquals(emptyList(), metadata.validateOccurrences(emptyList()))
    }

    @Test
    fun `parameters are extracted recursively from WITH derived JOIN and WHERE nodes`() {
        val withParameter = SqlParameter.of("with_value", 1)
        val derivedParameter = SqlParameter.of("derived_value", 2)
        val joinParameter = SqlParameter.of("join_value", 3)
        val whereParameter = SqlParameter.of("where_value", 4)
        val ast = QueryRenderSelectAst().apply {
            withs = QueryWithsAst().apply {
                withs += QueryWithsItemAst().apply {
                    withBody = QueryRenderSelectAst().apply {
                        where = whereOf(conditionWith(withParameter))
                    }
                }
            }
            table = QueryTableAst().apply {
                derivedQuery = QueryRenderSelectAst().apply {
                    where = whereOf(conditionWith(derivedParameter))
                }
            }
            joins = QueryJoinsAst().apply {
                joins += QueryJoinsItemAst().apply {
                    joinConditions = groupOf(conditionWith(joinParameter))
                }
            }
            where = whereOf(conditionWith(whereParameter))
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        listOf(withParameter, derivedParameter, joinParameter, whereParameter).forEach {
            assertTrue(metadata.containsDefinition(it))
        }
    }

    @Test
    fun `raw scalar and collection slots are legacy-only`() {
        val scalarAst = QueryRenderSelectAst().apply {
            where = whereOf(conditionWith("status = 'active'"))
        }
        val collectionAst = QueryRenderSelectAst().apply {
            where = whereOf(conditionWith(QueryConditionsCollectionAst().apply {
                paramsCollection += "legacy_name"
            }))
        }

        assertEquals(
            QueryParameterRepresentation.LegacyOnly,
            QueryParameterMetadataExtractor.inspect(scalarAst).representation,
        )
        assertEquals(
            QueryParameterRepresentation.LegacyOnly,
            QueryParameterMetadataExtractor.inspect(collectionAst).representation,
        )
        assertEquals(
            QueryParameterRepresentation.LegacyOnly,
            QueryParameterMetadataExtractor.inspect(QueryRenderSelectAst()).representation,
        )
    }

    @Test
    fun `typed and raw parameter sources are mixed or unsupported`() {
        val ast = QueryRenderSelectAst().apply {
            where = whereOf(
                conditionWith(SqlParameter.of("typed", 1)),
                conditionWith(QueryConditionsCollectionAst().apply {
                    paramsCollection += SqlParameter.of("collection", 2)
                    paramsCollection += "legacy_name"
                }),
            )
        }

        assertEquals(
            QueryParameterRepresentation.MixedOrUnsupported,
            QueryParameterMetadataExtractor.inspect(ast).representation,
        )
    }

    @Test
    fun `current insert and update column tags are legacy parameter sources`() {
        val insert = QueryRenderInsertAst().apply {
            columns += QueryColumnInsertAst().apply { columnTag = "insert_value" }
        }
        val update = QueryRenderUpdateAst().apply {
            columns += QueryColumnUpdateAst().apply { columnTag = "update_value" }
        }
        val mixedUpdate = QueryRenderUpdateAst().apply {
            columns += QueryColumnUpdateAst().apply { columnTag = "update_value" }
            where = whereOf(conditionWith(SqlParameter.of("where_value", 3)))
        }

        assertEquals(
            QueryParameterRepresentation.LegacyOnly,
            QueryParameterMetadataExtractor.inspect(insert).representation,
        )
        assertEquals(
            QueryParameterRepresentation.LegacyOnly,
            QueryParameterMetadataExtractor.inspect(update).representation,
        )
        assertEquals(
            QueryParameterRepresentation.MixedOrUnsupported,
            QueryParameterMetadataExtractor.inspect(mixedUpdate).representation,
        )
    }

    @Test
    fun `typed DML column parameters are identity definitions while untyped tags remain legacy`() {
        val insertParameter = SqlParameter.of("insert_value", 11)
        val updateParameter = SqlParameter.of("update_value", 22)
        val insert = QueryRenderInsertAst().apply {
            columns += QueryColumnInsertAst().apply {
                columnTag = "insert_value"
                parameter = insertParameter
            }
        }
        val update = QueryRenderUpdateAst().apply {
            columns += QueryColumnUpdateAst().apply {
                columnTag = "update_value"
                parameter = updateParameter
            }
        }
        val mixed = QueryRenderUpdateAst().apply {
            columns += QueryColumnUpdateAst().apply {
                columnTag = "typed_value"
                parameter = updateParameter
            }
            columns += QueryColumnUpdateAst().apply { columnTag = "legacy_value" }
        }

        val insertMetadata = QueryParameterMetadataExtractor.inspect(insert)
        val updateMetadata = QueryParameterMetadataExtractor.inspect(update)

        assertEquals(QueryParameterRepresentation.IdentityAware, insertMetadata.representation)
        assertTrue(insertMetadata.containsDefinition(insertParameter))
        assertEquals(QueryParameterRepresentation.IdentityAware, updateMetadata.representation)
        assertTrue(updateMetadata.containsDefinition(updateParameter))
        assertEquals(
            QueryParameterRepresentation.MixedOrUnsupported,
            QueryParameterMetadataExtractor.inspect(mixed).representation,
        )
    }

    @Test
    fun `delete WHERE conditions use the same identity-aware traversal`() {
        val parameter = SqlParameter.of("delete_value", 7)
        val ast = QueryRenderDeleteAst().apply {
            where = whereOf(conditionWith(parameter))
        }

        val metadata = QueryParameterMetadataExtractor.inspect(ast)

        assertEquals(QueryParameterRepresentation.IdentityAware, metadata.representation)
        assertTrue(metadata.containsDefinition(parameter))
    }

    @Test
    fun `unsupported scalar slot is classified without silent identity fallback`() {
        val ast = QueryRenderSelectAst().apply {
            where = whereOf(conditionWith(42))
        }

        assertEquals(
            QueryParameterRepresentation.MixedOrUnsupported,
            QueryParameterMetadataExtractor.inspect(ast).representation,
        )
    }

    @Test
    fun `unknown AST node fails fast with its type in the diagnostic`() {
        val error = assertFailsWith<UnsupportedOperationException> {
            QueryParameterMetadataExtractor.inspect(object : IQueryAst {})
        }

        assertTrue(error.message.orEmpty().contains("Unsupported query AST node"))
        assertTrue(error.message.orEmpty().contains("QueryParameterMetadataTest"))
    }

    @Test
    fun `membership accepts repeated references and rejects an equal external object`() {
        val parameter = SqlParameter.of("known", 1)
        val external = parameter.copy()
        val metadata = QueryParameterMetadataExtractor.inspect(
            QueryRenderSelectAst().apply {
                where = whereOf(conditionWith(parameter), conditionWith(parameter))
            },
        )

        assertEquals(listOf(parameter, parameter), metadata.validateOccurrences(listOf(parameter, parameter)))
        assertFailsWith<IllegalArgumentException> {
            metadata.validateOccurrences(listOf(external))
        }
    }

    @Test
    fun `occurrence validation preserves order multiplicity and snapshots its input`() {
        val first = SqlParameter.of("first", 1)
        val second = SqlParameter.of("second", 2)
        val metadata = QueryParameterMetadataExtractor.inspect(
            QueryRenderSelectAst().apply {
                where = whereOf(conditionWith(first), conditionWith(second))
            },
        )
        val input = mutableListOf(second, first, second)

        val validated = metadata.validateOccurrences(input)
        input.clear()

        assertEquals(3, validated.size)
        assertSame(second, validated[0])
        assertSame(first, validated[1])
        assertSame(second, validated[2])
    }

    @Test
    fun `occurrence count is validated independently of definition membership`() {
        val first = SqlParameter.of("first", 1)
        val second = SqlParameter.of("second", 2)
        val occurrences = listOf(first, second)
        val metadata = QueryParameterMetadataExtractor.inspect(
            QueryRenderSelectAst().apply {
                where = whereOf(conditionWith(first), conditionWith(second))
            },
        )

        metadata.validateOccurrenceCount(emittedParameterCount = 2, occurrences = occurrences)
        assertFailsWith<IllegalArgumentException> {
            metadata.validateOccurrenceCount(emittedParameterCount = 1, occurrences = occurrences)
        }
        assertFailsWith<IllegalArgumentException> {
            metadata.validateOccurrenceCount(emittedParameterCount = -1, occurrences = emptyList())
        }
    }

    private fun conditionWith(right: Any?) = QueryConditionsAst().apply {
        conditionSideRight = right
    }

    private fun whereOf(vararg conditions: IQueryIsConditionAst) = QueryWhereAst().apply {
        condition = groupOf(*conditions)
    }

    private fun groupOf(vararg conditions: IQueryIsConditionAst) = QueryConditionsGroupsAst().apply {
        this.conditions += conditions
    }
}
