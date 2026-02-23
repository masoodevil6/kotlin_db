package gog.my_project.data_base.query.builder.ast.update_builder.column_update

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst

class QueryColumnUpdateBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnUpdateAst = QueryColumnUpdateAst()
) : IQueryColumnUpdateApi {


    override fun <T> column(
        columnName: String,
        columnValue: T
    ): IQueryColumnUpdateApi {
        this.ast.columnName = "${columnName}";
        this.ast.columnTag = "${columnName}";
        this.ast.columnTag?.let { params += SqlParameter.of(it  , columnValue) }
        return this;
    }

    override fun <T> column(
        columnAlias: String,
        columnName: String,
        columnValue: T
    ): IQueryColumnUpdateApi {
        this.ast.columnAlias = "${columnAlias}";
        this.ast.columnName = "${columnName}";
        this.ast.columnTag = "${columnAlias}_${columnName}";
        this.ast.columnTag?.let { params += SqlParameter.of(it  , columnValue) }
        return this;
    }


}