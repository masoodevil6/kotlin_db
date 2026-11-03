package gog.my_project.data_base_query.ast.schema.select_schema.column_method.sum

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base_query.ast.metadata.select.column_method.sum.QueryColumnMethodSumAstMetadata

class QueryColumnMethodSumAst(
    override var metadata: QueryColumnMethodSumAstMetadata = QueryColumnMethodSumAstMetadata(),
) : IQueryColumnMethodSumAst {

    override var Column:   IQueryColumnsBaseAst? = null
    override var Over:     IQueryColumnOverAst? = null;

}
