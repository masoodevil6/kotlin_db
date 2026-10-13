package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.builder.ast.select_builder.metadata.QueryBuilderMetadata
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class QueryColumnInsertBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnInsertAst = QueryColumnInsertAst()
): IQueryColumnInsertApi {

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        columnValue: R,
    ): IQueryColumnInsertApi {
        QueryBuilderMetadata.requireTable(table)
        val columnMetadata = QueryBuilderMetadata.requireColumn(table, property)

        val columnName = QueryBuilderMetadata.columnName(table, property, columnMetadata)
        ast.columnName = columnName
        ast.columnTag = columnName
        val parameter = SqlParameter.of(columnName, columnValue)
        ast.parameter = parameter
        params += parameter
        return this
    }


    override fun <T> column(
        columnName: String,
        columnValue: T
    ): IQueryColumnInsertApi {
        this.ast.columnName = "${columnName}";
        this.ast.columnTag = "${columnName}";
        this.ast.columnTag?.let {
            val parameter = SqlParameter.of(it, columnValue)
            this.ast.parameter = parameter
            params += parameter
        }
        return this;
    }

}
