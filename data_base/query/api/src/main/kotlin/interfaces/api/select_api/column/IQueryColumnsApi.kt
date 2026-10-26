package gog.my_project.data_base.query.api.interfaces.api.select_api.column

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.concat.IQueryColumnMethodConcatApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.avg.IQueryColumnMethodAvgApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.count.IQueryColumnMethodCountApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.max.IQueryColumnMethodMaxApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.min.IQueryColumnMethodMinApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.sum.IQueryColumnMethodSumApi
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.enums.DataType
import kotlin.reflect.KProperty1

interface IQueryColumnsApi : IQueryApi<IQueryColumnsAst> {

    fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnsApi

    fun methodCount(blockMethodCount:   IQueryColumnMethodCountApi.()  -> Unit): IQueryColumnsApi
    fun methodConcat(blockMethodConcat: IQueryColumnMethodConcatApi.() -> Unit): IQueryColumnsApi
    fun methodMin(blockMethodMin:       IQueryColumnMethodMinApi.()    -> Unit): IQueryColumnsApi
    fun methodMax(blockMethodMax:       IQueryColumnMethodMaxApi.()    -> Unit): IQueryColumnsApi
    fun methodSum(blockMethodSum:       IQueryColumnMethodSumApi.()    -> Unit): IQueryColumnsApi
    fun methodAvg(blockMethodAvg:       IQueryColumnMethodAvgApi.()    -> Unit): IQueryColumnsApi

    fun alias(alias: String): IQueryColumnsApi

    /** Declares the published output name using a Query declaration property. */
    fun <T, R> alias(property: KProperty1<T, R>): IQueryColumnsApi {
        throw UnsupportedOperationException("Typed output aliases are not supported by this IQueryColumnsApi implementation")
    }

    /** Declares the explicit extraction type used when this SELECT output is materialized. */
    fun execute(dataType: DataType): IQueryColumnsApi
}
