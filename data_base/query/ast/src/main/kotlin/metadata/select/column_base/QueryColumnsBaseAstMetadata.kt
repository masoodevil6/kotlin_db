package gog.my_project.data_base.query.ast.metadata.select.column_base

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

class QueryColumnsBaseAstMetadata : IQueryAstMetadata {

    var tableAliasFromModel: String? = null
    var tableAliasFromAnnotation: String? = null
    var tableAliasFromAst: String? = null

    var columnNameFromModel: String? = null
    var columnNameFromAnnotation: String? = null
    var columnNameFromAst: String? = null

    var columnAliasFromModel: String? = null
    var columnAliasFromAnnotation: String? = null

    fun resolveTableAlias(): String? =
        tableAliasFromAst ?: tableAliasFromAnnotation ?: tableAliasFromModel

    fun resolveColumnName(): String? =
        columnNameFromAst ?: columnNameFromAnnotation ?: columnNameFromModel

    fun resolveColumnAlias(): String? =
        columnAliasFromAnnotation ?: columnAliasFromModel
}
