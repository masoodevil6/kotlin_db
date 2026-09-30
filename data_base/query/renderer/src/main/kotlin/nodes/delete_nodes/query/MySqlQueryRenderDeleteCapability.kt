package gog.my_project.data_base.query.renderer.nodes.delete_nodes.query

import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.dialect.data_class.delete_data.query_render_delete.QueryRenderDeleteData
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.dialect.data_class.select_data.where.QueryWhereData
import gog.my_project.data_base.query.dialect.nodes.insert_modes.query_render_delete.IQueryRenderDeleteCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryRenderDeleteCapability : IQueryRenderDeleteCapability {

    override fun render(
        ast: IQueryRenderDeleteAst,
        ctx: IRenderContext,
        dataClass: QueryRenderDeleteData?
    ): String? {

        val targets =  ast.targets;
        val queryBuilderTable =  ast.table;
        val queryBuilderWhere =  ast.where;

        var targetsStr = "";
        if (targets.size > 0) {
            for ((index, target) in targets.withIndex()){
                targetsStr +=  " $target ";

                if (index < targets.size - 1){
                    targetsStr += ","
                }
            }
        }

        return  " ${ctx.dialect._prefixDelete} $targetsStr" +
                " ${ctx.registry.render(queryBuilderTable , ctx.dialect  , QueryTableData(_withPrefix = true)) } " +
                " ${ctx.registry.render(queryBuilderWhere , ctx.dialect  , QueryWhereData()) } ";
    }

}