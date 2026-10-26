package gog.my_project.data_base.query.api.interfaces.api.select_api.option_partition

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst

interface IQueryOptionPartitionApi: IQueryApi<IQueryOptionPartitionAst> {

    fun addColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryOptionPartitionApi

}