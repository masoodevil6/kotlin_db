package gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnInsertApi : IQueryApi<IQueryColumnInsertAst> {

    // NEW AST-BASED IMPLEMENTATION: identify a column before defining its parameter value.
    fun column(columnName: String): IQueryColumnInsertApi

    // NEW AST-BASED IMPLEMENTATION: resolve column metadata without creating a legacy value.
    fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
    ): IQueryColumnInsertApi

    // NEW AST-BASED IMPLEMENTATION: stores the value definition as IQueryParameterAst<*>.
    fun setValue(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryColumnInsertApi

}
