package gog.my_project.data_base.query.api.interfaces.column_base

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnsBaseApi : IQueryApi {

    var ast: IQueryColumnsBaseAst;

    fun <T: IModel, R> column(table: KClass<T>, column: KProperty1<T, R>): IQueryColumnsBaseApi;

    fun <T: ICte, R> cteColumn(cte: KClass<T>, select: KProperty1<T, R>): IQueryColumnsBaseApi;


}