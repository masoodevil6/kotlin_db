package gog.my_project.data_base.query.ast.metadata.select.table

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata

class QueryTableAstMetadata : IQueryAstMetadata {
    var nameFromModel: String? = null
    var nameFromAnnotation: String? = null
    var nameFromAst: String? = null

    var aliasFromModel: String? = null
    var aliasFromAnnotation: String? = null
    var aliasFromAst: String? = null

    /** CTE identity recorded by the Builder; null for model, table, and ordinary derived SELECT sources. */
    var cteRuntimeName: String? = null
    var logicalQueryName: String? = null
    /** Distinguishes a composed Cte object source from a named cte(String) occurrence. */
    var isCteObjectSource: Boolean = false

    fun resolveName(): String? = nameFromAst ?: nameFromAnnotation ?: nameFromModel

    fun resolveAlias(): String? = aliasFromAst ?: aliasFromAnnotation ?: aliasFromModel
}
