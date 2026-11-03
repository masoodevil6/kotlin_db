package gog.my_project.data_base_query.ast.schema.select_schema.column_over

import gog.my_project.data_base_query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base_query.ast.metadata.select.column_over.QueryColumnOverAstMetadata

class QueryColumnOverAst(
    override var metadata: QueryColumnOverAstMetadata = QueryColumnOverAstMetadata(),
) : IQueryColumnOverAst {

    override var partitionBy: IQueryOptionPartitionAst?    = null;
    override var orderBy:     IQueryOptionOrderAst?        = null;

}