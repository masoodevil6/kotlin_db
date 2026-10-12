package gog.my_project.data_base.query.builder.ast.update_builder.column_update

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.builder.ast.select_builder.metadata.QueryBuilderMetadata
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class QueryColumnUpdateBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnUpdateAst = QueryColumnUpdateAst()
) : IQueryColumnUpdateApi {

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        columnValue: R,
        tableAlias: String?,
    ): IQueryColumnUpdateApi {
        val tableMetadata = QueryBuilderMetadata.requireTable(table)
        val columnMetadata = QueryBuilderMetadata.requireColumn(table, property)

        val effectiveAlias = tableAlias ?: QueryBuilderMetadata.tableAlias(table, tableMetadata)
        require(effectiveAlias.isNotEmpty()) {
            "UPDATE of ${table.qualifiedName}.${property.name} requires a non-empty table alias"
        }

        ast.columnAlias = effectiveAlias
        val columnName = QueryBuilderMetadata.columnName(table, property, columnMetadata)
        ast.columnName = columnName
        ast.columnTag = "${effectiveAlias}_${columnName}"
        params += SqlParameter.of(ast.columnTag!!, columnValue)
        return this
    }


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
