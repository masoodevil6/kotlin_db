package gog.my_project.data_base.query.ast.column_base

import gog.my_project.data_base.query.ast.metadata.select.column_base.QueryColumnsBaseAstMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryColumnsBaseAstMetadataTest {

    @Test
    fun `table alias resolves AST before annotation before model`() {
        val metadata = QueryColumnsBaseAstMetadata()
        assertNull(metadata.resolveTableAlias())

        metadata.tableAliasFromModel = "model_alias"
        assertEquals("model_alias", metadata.resolveTableAlias())

        metadata.tableAliasFromAnnotation = "annotation_alias"
        assertEquals("annotation_alias", metadata.resolveTableAlias())

        metadata.tableAliasFromAst = "ast_alias"
        assertEquals("ast_alias", metadata.resolveTableAlias())

        metadata.tableAliasFromAst = null
        assertEquals("annotation_alias", metadata.resolveTableAlias())
        metadata.tableAliasFromAnnotation = null
        assertEquals("model_alias", metadata.resolveTableAlias())
    }

    @Test
    fun `column name resolves AST before annotation before model`() {
        val metadata = QueryColumnsBaseAstMetadata()
        assertNull(metadata.resolveColumnName())

        metadata.columnNameFromModel = "model_column"
        assertEquals("model_column", metadata.resolveColumnName())

        metadata.columnNameFromAnnotation = "annotation_column"
        assertEquals("annotation_column", metadata.resolveColumnName())

        metadata.columnNameFromAst = "ast_column"
        assertEquals("ast_column", metadata.resolveColumnName())

        metadata.columnNameFromAst = null
        assertEquals("annotation_column", metadata.resolveColumnName())
        metadata.columnNameFromAnnotation = null
        assertEquals("model_column", metadata.resolveColumnName())
    }

    @Test
    fun `column alias resolves annotation before model`() {
        val metadata = QueryColumnsBaseAstMetadata()
        assertNull(metadata.resolveColumnAlias())

        metadata.columnAliasFromModel = "model_alias"
        assertEquals("model_alias", metadata.resolveColumnAlias())

        metadata.columnAliasFromAnnotation = "annotation_alias"
        assertEquals("annotation_alias", metadata.resolveColumnAlias())

        metadata.columnAliasFromAnnotation = null
        assertEquals("model_alias", metadata.resolveColumnAlias())
    }
}
