package gog.my_project.data_base.query.api.interfaces.api.select_api.column_base

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnsBaseApi : IQueryApi<IQueryColumnsBaseAst> {

    fun tableSelector(sourceName: String): IQueryColumnsBaseApi

    /** Selects a model or Query/CTE declaration source without generic-bound overload clashes. */
    fun tableSelector(source: KClass<*>): IQueryColumnsBaseApi

    fun columnSelector(columnName: String): IQueryColumnsBaseApi

    /** Resolves a model property or typed Query/CTE output from its declaring class. */
    fun columnSelector(property: KProperty1<*, *>): IQueryColumnsBaseApi

    /** Resolves a typed property while checking the explicitly supplied declaration. */
    fun columnSelector(declaration: KClass<*>, property: KProperty1<*, *>): IQueryColumnsBaseApi

    /** Compatibility entry point; new code should use tableSelector/columnSelector. */
    @Deprecated("Use tableSelector(sourceName).columnSelector(columnName)")
    fun tableColumn(tableAlias: String, columnName: String): IQueryColumnsBaseApi =
        tableSelector(tableAlias).columnSelector(columnName)

    /** Resolves a published SQL output while retaining the active CTE identity check. */
    @Deprecated("Use tableSelector/columnSelector with a typed Query output when available")
    fun cteColumn(cte: Cte, sqlOutputName: String): IQueryColumnsBaseApi

    /** Compatibility entry point for an unqualified SQL expression. */
    @Deprecated("Use columnSelector(expression)")
    fun tableAttribute(expression: String): IQueryColumnsBaseApi = columnSelector(expression)


}
