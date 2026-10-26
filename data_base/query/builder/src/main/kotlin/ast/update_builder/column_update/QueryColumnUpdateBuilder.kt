package gog.my_project.data_base.query.builder.ast.update_builder.column_update

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.builder.ast.parameter.QueryParameterBuilder
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class QueryColumnUpdateBuilder(
    override var ast: IQueryColumnUpdateAst = QueryColumnUpdateAst()
) : IQueryColumnUpdateApi {

    override fun column(columnName: String): IQueryColumnUpdateApi {
        ast.metadata.columnAlias = ""
        ast.metadata.columnName = columnName
        ast.metadata.columnTag = columnName
        return this
    }

    override fun column(columnAlias: String, columnName: String): IQueryColumnUpdateApi {
        ast.metadata.columnAlias = columnAlias
        ast.metadata.columnName = columnName
        ast.metadata.columnTag = "${columnAlias}_${columnName}"
        return this
    }

    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        tableAlias: String?,
    ): IQueryColumnUpdateApi {
        ModelMetadataResolver.requireTable(table)
        ModelMetadataResolver.requireColumn(table, property)

        val effectiveAlias = tableAlias ?: ModelMetadataResolver.tableAlias(table)
        require(effectiveAlias.isNotEmpty()) {
            "UPDATE of ${table.qualifiedName}.${property.name} requires a non-empty table alias"
        }

        val columnName = ModelMetadataResolver.columnName(table, property)
        ast.metadata.columnAlias = effectiveAlias
        ast.metadata.columnName = columnName
        ast.metadata.columnTag = "${effectiveAlias}_${columnName}"
        return this
    }

    override fun setValue(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryColumnUpdateApi {
        check(ast.metadata.columnName != null && ast.metadata.columnTag != null) {
            "UPDATE column must be selected before setValue"
        }
        check(ast.parameterAst == null) {
            "UPDATE column value has already been defined"
        }

        val parameterAst = QueryParameterAst<Any?>()
        val parameterBuilder = QueryParameterBuilder(parameterAst) { ast.metadata.columnTag!! }
        parameterBuilder.apply(blockParameter)
        parameterBuilder.validate()
        ast.parameterAst = parameterAst
        return this
    }



}
