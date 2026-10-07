package gog.my_project.data_base.query.api.interfaces.api.update_api.column_update

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnUpdateApi : IQueryApi<IQueryColumnUpdateAst> {

    fun <T> column(columnName: String , columnValue: T) : IQueryColumnUpdateApi;
    fun <T> column(columnAlias: String , columnName: String , columnValue: T) : IQueryColumnUpdateApi;

    fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        columnValue: R,
        tableAlias: String? = null,
    ): IQueryColumnUpdateApi

}
