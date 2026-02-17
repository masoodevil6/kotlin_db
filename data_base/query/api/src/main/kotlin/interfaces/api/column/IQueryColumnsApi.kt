package gog.my_project.data_base.query.api.interfaces.api.column

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst

interface IQueryColumnsApi : IQueryApi {

    var ast: IQueryColumnsAst;

    fun method(method: SqlMethodColumn) :IQueryColumnsApi;
    fun sum() :IQueryColumnsApi;
    fun count() :IQueryColumnsApi;
    fun avg() :IQueryColumnsApi;
    fun min() :IQueryColumnsApi;
    fun max() :IQueryColumnsApi;

    fun column( blockColumn: IQueryColumnsBaseApi.() -> Unit):IQueryColumnsApi;

    fun alias(alias: String):IQueryColumnsApi;

}