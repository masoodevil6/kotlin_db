package gog.my_project.data_base_query.ast.schema.select_schema.column_method.avg

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base_query.ast.metadata.select.column_method.avg.QueryColumnMethodAvgAstMetadata

class QueryColumnMethodAvgAst(
    override var metadata: QueryColumnMethodAvgAstMetadata = QueryColumnMethodAvgAstMetadata(),
) : IQueryColumnMethodAvgAst {
    override var Column: IQueryColumnsBaseAst? = null
    override var Over: IQueryColumnOverAst? = null;
}
