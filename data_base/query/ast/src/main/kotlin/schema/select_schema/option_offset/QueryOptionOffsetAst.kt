package gog.my_project.data_base.query.ast.schema.select_schema.option_offset
import gog.my_project.data_base.query.ast.metadata.select.option_offset.QueryOptionOffsetAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst

class QueryOptionOffsetAst(
    override var metadata: QueryOptionOffsetAstMetadata = QueryOptionOffsetAstMetadata()
) : IQueryOptionOffsetAst {

}
