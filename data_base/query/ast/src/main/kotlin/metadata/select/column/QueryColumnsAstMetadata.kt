package gog.my_project.data_base.query.ast.metadata.select.column

import gog.my_project.data_base.query.ast.metadata.interfaces.IQueryAstMetadata
import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.reflect.KProperty1

class QueryColumnsAstMetadata : IQueryAstMetadata {
    var ColumnMethod:             String? = null
    var ColumnAlias:              String? = null
    var IsColumnAliasExplicit:     Boolean = false
    var ExecutionType:            DataType? = null

    var IsExecutionTypeExplicit:  Boolean = false
    var PropertyReference:        KProperty1<*, *>? = null
    var logicalOutputName:        String? = null
}
