package gog.my_project.data_base.query.builder.ast.update_builder.column_update

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

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

        val effectiveAlias = tableAlias ?: tableMetadata.alias
        require(effectiveAlias.isNotEmpty()) {
            "UPDATE of ${table.qualifiedName}.${property.name} requires a non-empty table alias"
        }

        ast.columnAlias = effectiveAlias
        ast.columnName = columnMetadata.name
        ast.columnTag = "${effectiveAlias}_${columnMetadata.name}"
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
