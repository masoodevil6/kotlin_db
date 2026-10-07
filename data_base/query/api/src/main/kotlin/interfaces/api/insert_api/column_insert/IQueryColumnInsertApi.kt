package gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnInsertApi : IQueryApi<IQueryColumnInsertAst> {

    fun <T> column(columnName: String , columnValue: T) : IQueryColumnInsertApi

    fun <T : IModelBase, R> column(
        table: KClass<T>,
        property: KProperty1<T, R>,
        columnValue: R,
    ): IQueryColumnInsertApi

}
