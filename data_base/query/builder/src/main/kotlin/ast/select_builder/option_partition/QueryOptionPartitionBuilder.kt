package gog.my_project.data_base.query.builder.ast.select_builder.option_partition

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_partition.IQueryOptionPartitionApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAstPart
import gog.my_project.data_base.query.ast.schema.select_schema.option_partition.QueryOptionPartitionAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder

class QueryOptionPartitionBuilder(
    override var ast: IQueryOptionPartitionAst = QueryOptionPartitionAst()
) : IQueryOptionPartitionApi {

    override fun addColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryOptionPartitionApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.parts.add(
            IQueryOptionPartitionAstPart.Column(columnAst)
        )
        return this;
    }

}