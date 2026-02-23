package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst

class QueryColumnInsertBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnInsertAst = QueryColumnInsertAst()
): IQueryColumnInsertApi {


    override fun <T> column(
        columnName: String,
        columnValue: T
    ): IQueryColumnInsertApi {
        this.ast.columnName = "${columnName}";
        this.ast.columnTag = "${columnName}";
        this.ast.columnTag?.let { params += SqlParameter.of(it  , columnValue) }
        return this;
    }

}