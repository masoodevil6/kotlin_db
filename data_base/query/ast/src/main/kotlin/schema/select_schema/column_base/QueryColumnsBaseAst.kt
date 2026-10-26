package gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base

import gog.my_project.data_base.query.ast.metadata.select.column_base.QueryColumnsBaseAstMetadata
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst

class QueryColumnsBaseAst(
    override var metadata: QueryColumnsBaseAstMetadata = QueryColumnsBaseAstMetadata(),
) : IQueryColumnsBaseAst {

}
