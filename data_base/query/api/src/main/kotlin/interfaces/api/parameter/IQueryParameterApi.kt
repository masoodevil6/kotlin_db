package gog.my_project.data_base.query.api.interfaces.api.parameter

/** DSL surface for defining one scalar parameter in a query. */
interface IQueryParameterApi<T> {

    fun paramName(name: String): IQueryParameterApi<T>

    fun paramValue(value: T): IQueryParameterApi<T>

}
