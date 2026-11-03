package gog.my_project.data_base_query.ast.interfaces.select_interface.column_over

import gog.my_project.data_base_query.ast.interfaces.IQueryAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.option_order.IQueryOptionOrderAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base_query.ast.metadata.select.column_over.QueryColumnOverAstMetadata

interface IQueryColumnOverAst : IQueryAst<QueryColumnOverAstMetadata> {

    var partitionBy: IQueryOptionPartitionAst?
    var orderBy:     IQueryOptionOrderAst?

}