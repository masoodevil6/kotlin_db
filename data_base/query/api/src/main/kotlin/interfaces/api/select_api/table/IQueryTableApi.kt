package gog.my_project.data_base.query.api.interfaces.api.select_api.table

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import kotlin.reflect.KClass

interface IQueryTableApi : IQueryApi<IQueryTableAst> {

    //fun <T: IModelBase> table(table: KClass<T>): IQueryTableApi;
    //fun <T: ICte> cte(cte: KClass<T>): IQueryTableApi;

    fun table(
        table: String ,
        alias: String
    ): IQueryTableApi;

    fun table(
        table: String
    ): IQueryTableApi;


    fun cte(
        cte: String ,
        alias: String
    ): IQueryTableApi;

    fun cte(
        cte: String
    ): IQueryTableApi;

}