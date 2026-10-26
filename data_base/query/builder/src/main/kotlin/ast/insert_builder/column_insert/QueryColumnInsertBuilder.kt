package gog.my_project.data_base.query.builder.ast.insert_builder.column_insert

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.builder.ast.parameter.QueryParameterBuilder
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class QueryColumnInsertBuilder(
    override var ast: IQueryColumnInsertAst = QueryColumnInsertAst()
): IQueryColumnInsertApi {


    /// Choose column string
    override fun column(columnName: String): IQueryColumnInsertApi {
        ast.metadata.columnName = columnName
        ast.metadata.columnTag = columnName
        return this
    }

    /// Choose column model
    override fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
    ): IQueryColumnInsertApi {
        ModelMetadataResolver.requireTable(table)
        ModelMetadataResolver.requireColumn(table, property)

        val columnName = ModelMetadataResolver.columnName(table, property)
        ast.metadata.columnName = columnName
        ast.metadata.columnTag = columnName
        return this
    }

    /// set value param
    override fun setValue(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryColumnInsertApi {
        check(ast.metadata.columnName != null && ast.metadata.columnTag != null) {
            "INSERT column must be selected before setValue"
        }
        check(ast.parameterAst == null) {
            "INSERT column value has already been defined"
        }

        val parameterAst = QueryParameterAst<Any?>()
        val parameterBuilder = QueryParameterBuilder(parameterAst) { ast.metadata.columnTag!! }
        parameterBuilder.apply(blockParameter)
        parameterBuilder.validate()
        ast.parameterAst = parameterAst
        return this
    }



}
