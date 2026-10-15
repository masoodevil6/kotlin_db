package gog.my_project.data_base.query.api.metadata

import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectiveQueryMetadataContractTest {

    @Test
    fun emptyRelationListIsPreserved() {
        val metadata = EffectiveQueryMetadata(relations = emptyList())

        assertEquals(emptyList(), metadata.relations)
        assertEquals(emptyList(), metadata.outputs)
    }

    @Test
    fun finalSelectOutputsArePreservedIndependentlyOfRelations() {
        val outputs = listOf(
            EffectiveOutputMetadata(null, "user_id", DataType.LONG),
            EffectiveOutputMetadata(null, "user_name", DataType.STRING),
        )
        val metadata = EffectiveQueryMetadata(relations = emptyList(), outputs = outputs)

        assertEquals(emptyList(), metadata.relations)
        assertEquals(outputs, metadata.outputs)
    }

    @Test
    fun relationAndOutputValuesArePreserved() {
        val output = EffectiveOutputMetadata(
            logicalOutputName = "logical_age",
            publishedSqlAlias = "age",
            executionType = DataType.LONG,
        )
        val relation = EffectiveRelationMetadata(
            logicalQueryName = "user_query",
            cteRuntimeName = "user_query_runtime",
            withSqlName = "user_query_sql",
            occurrenceAlias = "users",
            outputs = listOf(output),
        )

        assertEquals(
            EffectiveQueryMetadata(relations = listOf(relation)),
            EffectiveQueryMetadata(relations = listOf(relation)),
        )
        assertEquals("logical_age", relation.outputs.single().logicalOutputName)
        assertEquals("age", relation.outputs.single().publishedSqlAlias)
        assertEquals(DataType.LONG, relation.outputs.single().executionType)
    }

    @Test
    fun relationAndOutputOrderIsPreserved() {
        val firstOutput = EffectiveOutputMetadata("first", "first_sql", DataType.INT)
        val secondOutput = EffectiveOutputMetadata("second", "second_sql", DataType.STRING)
        val firstRelation = EffectiveRelationMetadata("first_query", "first_cte", null, "first", listOf(firstOutput))
        val secondRelation = EffectiveRelationMetadata("second_query", "second_cte", null, "second", listOf(secondOutput))

        val metadata = EffectiveQueryMetadata(
            relations = listOf(firstRelation, secondRelation),
        )

        assertEquals(listOf(firstRelation, secondRelation), metadata.relations)
        assertEquals(listOf(firstOutput), metadata.relations[0].outputs)
        assertEquals(listOf(secondOutput), metadata.relations[1].outputs)
    }

    @Test
    fun nullableFieldsRemainNull() {
        val output = EffectiveOutputMetadata(
            logicalOutputName = null,
            publishedSqlAlias = null,
            executionType = null,
        )
        val relation = EffectiveRelationMetadata(
            logicalQueryName = null,
            cteRuntimeName = null,
            withSqlName = null,
            occurrenceAlias = null,
            outputs = listOf(output),
        )

        assertEquals(null, relation.logicalQueryName)
        assertEquals(null, relation.cteRuntimeName)
        assertEquals(null, relation.withSqlName)
        assertEquals(null, relation.occurrenceAlias)
        assertEquals(null, output.logicalOutputName)
        assertEquals(null, output.publishedSqlAlias)
        assertEquals(null, output.executionType)
    }

    @Test
    fun emptyStringsArePreservedWithoutNormalization() {
        val output = EffectiveOutputMetadata(
            logicalOutputName = "",
            publishedSqlAlias = "",
            executionType = null,
        )
        val relation = EffectiveRelationMetadata(
            logicalQueryName = "",
            cteRuntimeName = "",
            withSqlName = "",
            occurrenceAlias = "",
            outputs = listOf(output),
        )

        assertEquals("", relation.logicalQueryName)
        assertEquals("", relation.cteRuntimeName)
        assertEquals("", relation.withSqlName)
        assertEquals("", relation.occurrenceAlias)
        assertEquals("", output.logicalOutputName)
        assertEquals("", output.publishedSqlAlias)
    }

    @Test
    fun executionTypeUsesExistingDataType() {
        val output = EffectiveOutputMetadata(
            logicalOutputName = "age",
            publishedSqlAlias = "age",
            executionType = DataType.LONG,
        )

        assertEquals(DataType.LONG, output.executionType)
    }

    @Test
    fun customProviderReturnsItsMetadata() {
        val expected = EffectiveQueryMetadata(relations = emptyList())
        val provider = object : EffectiveQueryMetadataProvider {
            override fun effectiveQueryMetadata(): EffectiveQueryMetadata = expected
        }

        assertEquals(expected, provider.effectiveQueryMetadata())
    }

    @Test
    fun providerImplementationCanBeDetectedWithoutDetailsBehavior() {
        val providerValue: Any = object : EffectiveQueryMetadataProvider {
            override fun effectiveQueryMetadata(): EffectiveQueryMetadata =
                EffectiveQueryMetadata(relations = emptyList())
        }
        val nonProviderValue: Any = Any()

        assertTrue(providerValue is EffectiveQueryMetadataProvider)
        assertFalse(nonProviderValue is EffectiveQueryMetadataProvider)
    }
}
