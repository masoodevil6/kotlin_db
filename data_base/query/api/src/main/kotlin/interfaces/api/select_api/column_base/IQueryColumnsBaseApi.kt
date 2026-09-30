package gog.my_project.data_base.query.api.interfaces.api.select_api.column_base

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnsBaseApi : IQueryApi<IQueryColumnsBaseAst> {

    //fun <T: IModelBase, R> column(table: KClass<T>, column: KProperty1<T, R>): IQueryColumnsBaseApi;

    //fun <T: ICte, R> cteColumn(cte: KClass<T>, select: KProperty1<T, R>): IQueryColumnsBaseApi;


    fun  tableAttribute(tableAttribute: String): IQueryColumnsBaseApi;

    fun  tableColumn(tableAlias: String, columnName: String): IQueryColumnsBaseApi;

    fun  cteColumn(cteAlias: String, selectName: String): IQueryColumnsBaseApi;


}