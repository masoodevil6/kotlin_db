package gog.my_project.data_base.query.api.interfaces.api.update_api.column_update

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnUpdateApi : IQueryApi<IQueryColumnUpdateAst> {

    // NEW AST-BASED IMPLEMENTATION: select a raw, unqualified column before setting its value.
    fun column(columnName: String): IQueryColumnUpdateApi

    // NEW AST-BASED IMPLEMENTATION: select a raw column with its UPDATE occurrence alias.
    fun column(columnAlias: String, columnName: String): IQueryColumnUpdateApi

    // NEW AST-BASED IMPLEMENTATION: resolve Model column metadata without defining a value.
    fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        tableAlias: String? = null,
    ): IQueryColumnUpdateApi

    // NEW AST-BASED IMPLEMENTATION: stores the value definition as IQueryParameterAst<*>.
    fun setValue(blockParameter: IQueryParameterApi<Any?>.() -> Unit): IQueryColumnUpdateApi

}
