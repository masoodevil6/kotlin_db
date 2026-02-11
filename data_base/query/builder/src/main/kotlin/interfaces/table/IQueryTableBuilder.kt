package gog.my_project.data_base.query.builder.interfaces.table

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import kotlin.reflect.KClass

interface IQueryTableBuilder : IQuery {

    var ast: IQueryTableAst;

    fun <T: IModel> table(table: KClass<T>): IQueryTableBuilder;
    fun <T: ICte> cte(cte: KClass<T>): IQueryTableBuilder;

}