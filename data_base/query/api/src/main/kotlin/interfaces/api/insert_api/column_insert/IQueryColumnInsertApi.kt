package gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst

interface IQueryColumnInsertApi : IQueryApi<IQueryColumnInsertAst> {

    fun <T> column(columnName: String , columnValue: T) : IQueryColumnInsertApi

}