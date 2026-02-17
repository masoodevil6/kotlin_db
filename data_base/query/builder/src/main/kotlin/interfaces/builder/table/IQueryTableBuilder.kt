package gog.my_project.data_base.query.builder.interfaces.builder.table

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import kotlin.reflect.KClass

interface IQueryTableBuilder : IQuery {

    var ast: IQueryTableAst;

    fun <T: IModelBase> table(table: KClass<T>): IQueryTableBuilder;
    fun <T: ICte> cte(cte: KClass<T>): IQueryTableBuilder;

}