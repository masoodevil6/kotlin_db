package gog.my_project.data_base.query.builder.ast.select_builder.withs_item

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder

class QueryWithsItemBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryWithsItemAst = QueryWithsItemAst(),
): IQueryWithsItemApi {




    override fun with(
        withName: String,
        withClass: ICte
    ): gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
    {

//        val cteName = with::class.findAnnotation<QBCte>()
//            ?: error("CteName annotation missing on ${with::class.simpleName}")
//        this.ast.withName = cteName.name

        this.ast.withName = withName
        this.ast.withBody = withClass.cteQuery(params).ast

        return this;
    }



    override fun with(
        withName: String,
        blockWith: IQueryRenderSelectApi.() -> Unit
    ): IQueryWithsItemApi {
        this.ast.withName = withName;

        val ast = QueryRenderSelectAst();
        QueryRenderSelectBuilder(
            params,
            ast
        ).apply(blockWith);
        this.ast.withBody = ast;

        return this;
    }



}