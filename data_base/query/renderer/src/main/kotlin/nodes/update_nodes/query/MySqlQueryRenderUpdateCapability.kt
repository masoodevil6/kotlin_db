package gog.my_project.data_base.query.renderer.nodes.update_nodes.query

import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.dialect.data_class.select_data.where.QueryWhereData
import gog.my_project.data_base.query.dialect.data_class.update_data.column_update.QueryColumnUpdateData
import gog.my_project.data_base.query.dialect.data_class.update_data.query_render_update.QueryRenderUpdateData
import gog.my_project.data_base.query.dialect.nodes.update_nodes.query_render_update.IQueryRenderUpdateCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.renderParameter

class MySqlQueryRenderUpdateCapability : IQueryRenderUpdateCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast: IQueryRenderUpdateAst,
        ctx: IRenderContext,
        dataClass: QueryRenderUpdateData?
    ): String? {

        val queryBuilderTable =  ast.table;
        val columnsList =        ast.columns;
        val queryBuilderWhere =  ast.where;

        if (columnsList.size > 0) {
            var columnsStr = "";

            for ((index, column) in columnsList.withIndex()){

                val columnName =
                    ctx.registry.render(
                        column ,
                        ctx.dialect ,
                        QueryColumnUpdateData()
                    );
                if (columnName != null){
                    val parameter = checkNotNull(column.parameterAst) {
                        "UPDATE column '$columnName' is missing required parameterAst metadata"
                    }
                    val placeholder = ctx.renderParameter(parameter)
                    columnsStr +=  " $columnName = $placeholder ";

                    if (index < columnsList.size - 1){
                        columnsStr += ","
                    }
                }
            }

            return  " ${ctx.dialect._prefixUpdate} " +
                    " ${ctx.registry.render(queryBuilderTable , ctx.dialect  , QueryTableData(_withPrefix = false)) } " +
                    " set ${columnsStr} " +
                    " ${ctx.registry.render(queryBuilderWhere , ctx.dialect  , QueryWhereData()) } ";
        }
        return "";

    }

}
