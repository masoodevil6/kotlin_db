package gog.my_project.data_base.query.api.interfaces.api.select_api.column_over

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_partition.IQueryOptionPartitionApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_over.IQueryColumnOverAst

interface IQueryColumnOverApi: IQueryApi<IQueryColumnOverAst> {

    fun partition(blockPartition: IQueryOptionPartitionApi.() -> Unit): IQueryColumnOverApi;

    fun order(blockOrder: IQueryOptionOrderApi.() -> Unit): IQueryColumnOverApi;

}