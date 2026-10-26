package gog.my_project.data_base.query.builder.ast.select_builder.column_over

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_partition.IQueryOptionPartitionApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_partition.QueryOptionPartitionAst
import gog.my_project.data_base.query.builder.ast.select_builder.option_order.QueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_partition.QueryOptionPartitionBuilder

class QueryColumnOverBuilder(
    override var ast: IQueryColumnOverAst
) : IQueryColumnOverApi {

    override fun partition(blockPartition: IQueryOptionPartitionApi.() -> Unit): IQueryColumnOverApi {
        val partitionAst = QueryOptionPartitionAst()
        QueryOptionPartitionBuilder(partitionAst).apply(blockPartition)
        ast.partitionBy = partitionAst
        return this
    }

    override fun order(blockOrder: IQueryOptionOrderApi.() -> Unit): IQueryColumnOverApi {
        val orderAst = QueryOptionOrderAst()
        QueryOptionOrderBuilder(orderAst).apply(blockOrder)
        ast.orderBy = orderAst
        return this
    }

}