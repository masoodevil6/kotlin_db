package gog.my_project.data_base.query.example.metadata

import gog.my_project.data_base.query.api.metadata.EffectiveOutputMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectiveQueryMetadataTableTest {

    @Test
    fun `formatter displays final select outputs without a synthetic relation`() {
        val metadata = EffectiveQueryMetadata(
            relations = emptyList(),
            outputs = listOf(
                EffectiveOutputMetadata(
                    logicalOutputName = null,
                    publishedSqlAlias = "user_id",
                    executionType = DataType.LONG,
                    tableName = "user_users",
                    tableAlias = "uu",
                ),
            ),
        )

        val formatted = formatEffectiveQueryMetadata(metadata)

        assertTrue(!formatted.contains("Relations"), formatted)
        assertTrue(!formatted.contains("Relation outputs"), formatted)
        assertTrue(formatted.contains("Final SELECT outputs"), formatted)
        val columns = listOf(
            "tableName",
            "tableAlias",
            "logicalOutputName",
            "publishedSqlAlias",
            "executionType",
        )
        val columnPositions = columns.map { formatted.indexOf(it) }
        assertTrue(columnPositions.all { it >= 0 }, formatted)
        assertEquals(columnPositions.sorted(), columnPositions)
        val outputRow = formatted.lineSequence().single { it.startsWith("| 1 |") }
        val valuePositions = listOf("user_users", "uu", "<null>", "user_id", "LONG")
            .map { outputRow.indexOf(it) }
        assertTrue(valuePositions.all { it >= 0 }, outputRow)
        assertEquals(valuePositions.sorted(), valuePositions)
    }

    @Test
    fun `formatter keeps empty final outputs concise`() {
        val formatted = formatEffectiveQueryMetadata(
            EffectiveQueryMetadata(relations = emptyList(), outputs = emptyList()),
        )

        assertTrue(formatted.contains("Final SELECT outputs\n<none>"), formatted)
        assertTrue(!formatted.contains("Relations"), formatted)
        assertTrue(!formatted.contains("Relation outputs"), formatted)
    }
}
