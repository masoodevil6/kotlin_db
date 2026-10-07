package gog.my_project.data_base.query.api.interfaces.api.select_api.column

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi

import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnsApi : IQueryApi<IQueryColumnsAst> {

    fun method(method: SqlMethodColumn) : IQueryColumnsApi;
    fun sum() : IQueryColumnsApi;
    fun count() : IQueryColumnsApi;
    fun avg() : IQueryColumnsApi;
    fun min() : IQueryColumnsApi;
    fun max() : IQueryColumnsApi;

    /** Declares the explicit extraction type used when this SELECT output is materialized. */
    fun execute(dataType: DataType): IQueryColumnsApi;

    fun column( blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnsApi;

    fun <T : IModelBase, R> column(table: KClass<T>, property: KProperty1<T, R>): IQueryColumnsApi;

    fun relationColumn(
        relation: QueryRelation,
        sqlOutputName: String,
    ): IQueryColumnsApi {
        throw UnsupportedOperationException("QueryRelation fields are not supported by this IQueryColumnsApi implementation")
    }

    /** Names a QueryRelation output using the owning declaration property's name. */
    fun <T, R> relationColumn(property: KProperty1<T, R>, qualifier: String? = null): IQueryColumnsApi {
        throw UnsupportedOperationException("Typed QueryRelation fields are not supported by this IQueryColumnsApi implementation")
    }

    fun alias(alias: String): IQueryColumnsApi;

    /** Declares a QueryRelation output using the owning declaration property's name. */
    fun <T, R> alias(property: KProperty1<T, R>): IQueryColumnsApi {
        throw UnsupportedOperationException("Typed QueryRelation aliases are not supported by this IQueryColumnsApi implementation")
    }

}
