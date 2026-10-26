package gog.my_project.data_base.query.ast.schema.insert_schema.column
import gog.my_project.data_base.query.ast.metadata.insert.column_insert.QueryColumnInsertAstMetadata

import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

class QueryColumnInsertAst(
    override var metadata: QueryColumnInsertAstMetadata = QueryColumnInsertAstMetadata()
) : IQueryColumnInsertAst {

    override var parameterAst: IQueryParameterAst<*>? = null

}
