package gog.my_project.data_base.query.api.interfaces.api.update_api.column_update

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst

interface IQueryColumnUpdateApi : IQueryApi<IQueryColumnUpdateAst> {

    fun <T> column(columnName: String , columnValue: T) : IQueryColumnUpdateApi;
    fun <T> column(columnAlias: String , columnName: String , columnValue: T) : IQueryColumnUpdateApi;

}