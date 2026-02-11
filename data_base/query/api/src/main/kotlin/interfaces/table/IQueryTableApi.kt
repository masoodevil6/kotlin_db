package gog.my_project.data_base.query.api.interfaces.table

import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import kotlin.reflect.KClass

interface IQueryTableApi : IQueryApi {

    var ast: IQueryTableAst;

    fun <T: IModel> table(table: KClass<T>): IQueryTableApi;
    fun <T: ICte> cte(cte: KClass<T>): IQueryTableApi;

}