package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

class QueryColumnInsertBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryColumnInsertAst = QueryColumnInsertAst()
): IQueryColumnInsertApi {

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        columnValue: R,
    ): IQueryColumnInsertApi {
        val tableMetadata = table.findAnnotation<QBTable>()
            ?: throw IllegalArgumentException("${table.qualifiedName} must be annotated with @QBTable")
        require(tableMetadata.name.isNotEmpty()) {
            "@QBTable on ${table.qualifiedName} must declare a non-empty table name"
        }

        val columnMetadata = property.findAnnotation<QBColumn>()
            ?: throw IllegalArgumentException("${property.name} on ${table.qualifiedName} must be annotated with @QBColumn")
        require(columnMetadata.name.isNotEmpty()) {
            "@QBColumn on ${table.qualifiedName}.${property.name} must declare a non-empty column name"
        }

        ast.columnName = columnMetadata.name
        ast.columnTag = columnMetadata.name
        params += SqlParameter.of(columnMetadata.name, columnValue)
        return this
    }


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
