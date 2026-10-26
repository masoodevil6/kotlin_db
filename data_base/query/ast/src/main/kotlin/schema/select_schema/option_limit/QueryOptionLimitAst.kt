package gog.my_project.data_base.query.ast.schema.select_schema.option_limit
import gog.my_project.data_base.query.ast.metadata.select.option_limit.QueryOptionLimitAstMetadata

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_limit.IQueryOptionLimitAst

class QueryOptionLimitAst(
    override var metadata: QueryOptionLimitAstMetadata = QueryOptionLimitAstMetadata()
) : IQueryOptionLimitAst {

}
