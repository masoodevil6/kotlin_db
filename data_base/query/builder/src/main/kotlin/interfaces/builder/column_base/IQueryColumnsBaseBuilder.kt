package gog.my_project.data_base.query.builder.interfaces.builder.column_base

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

interface IQueryColumnsBaseBuilder : IQuery {

    var ast: IQueryColumnsBaseAst;

    fun <T: IModelBase, R> column(table: KClass<T>, column: KProperty1<T, R>): IQueryColumnsBaseBuilder;

    fun <T: ICte, R> cteColumn(cte: KClass<T>, select: KProperty1<T, R>): IQueryColumnsBaseBuilder;

}