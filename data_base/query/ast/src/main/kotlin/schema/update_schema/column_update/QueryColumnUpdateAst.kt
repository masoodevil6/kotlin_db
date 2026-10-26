package gog.my_project.data_base.query.ast.schema.update_schema.column_update
import gog.my_project.data_base.query.ast.metadata.update.column_update.QueryColumnUpdateAstMetadata

import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

class QueryColumnUpdateAst(
    override var metadata: QueryColumnUpdateAstMetadata = QueryColumnUpdateAstMetadata()
) : IQueryColumnUpdateAst {

    override var parameterAst: IQueryParameterAst<*>? = null
}
