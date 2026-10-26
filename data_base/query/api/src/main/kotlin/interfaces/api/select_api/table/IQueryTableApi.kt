package gog.my_project.data_base.query.api.interfaces.api.select_api.table

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import kotlin.reflect.KClass

interface IQueryTableApi : IQueryApi<IQueryTableAst> {

    /** Existing typed table entry point retained for CRUD/JOIN compatibility. */
    fun <T : IModelBase> table(table: KClass<T>): IQueryTableApi;
    fun <T : IModelBase> model(model: KClass<T>): IQueryTableApi
    //fun <T: ICte> cte(cte: KClass<T>): IQueryTableApi;

    fun table(table: String): IQueryTableApi;

    fun table(blockQuery: IQueryRenderSelectApi.() -> Unit): IQueryTableApi

    fun cte(cte: Cte): IQueryTableApi

    fun cte(cte: String): IQueryTableApi;

    fun alias(alias: String): IQueryTableApi;

}
