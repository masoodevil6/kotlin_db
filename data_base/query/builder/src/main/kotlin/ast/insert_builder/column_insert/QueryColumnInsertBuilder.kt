package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.meta_data.models.ModelMetadataResolver
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
        ModelMetadataResolver.requireTable(table)
        ModelMetadataResolver.requireColumn(table, property)

        val columnName = ModelMetadataResolver.columnName(table, property)
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
