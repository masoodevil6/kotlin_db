package gog.my_project.data_base.query.renderer.nodes.insert_nodes.query

import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.dialect.data_class.insert_data.column_insert.QueryColumnInsertData
import gog.my_project.data_base.query.dialect.data_class.insert_data.query_render_insert.QueryRenderInsertData
import gog.my_project.data_base.query.dialect.nodes.insert_nodes.query_render_insert.IQueryRenderInsertCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryRenderInsertCapability() : IQueryRenderInsertCapability {

    override fun render(
        ast:        IQueryRenderInsertAst,
        ctx:        IRenderContext,
        dataClass:  QueryRenderInsertData?
    ): String? {
        val table = ast.table;
        val columnsList = ast.columns;
        if (columnsList.size > 0) {
            var columnsReferenceStr = "";
            var columnsValuesStr = "";

            for ((index, column) in columnsList.withIndex()){

                val columnName =
                    ctx.registry.render(
                        column ,
                        ctx.dialect ,
                        QueryColumnInsertData()
                    );

                if (columnName != null){
                    columnsReferenceStr +=  " $columnName ";
                    columnsValuesStr +=  " :$columnName ";

                    if (index < columnsList.size - 1){
                        columnsReferenceStr += ","
                        columnsValuesStr += ","
                    }
                }
            }

            return " ${ctx.dialect._prefixInsert} ${table} ($columnsReferenceStr) values ($columnsValuesStr)";
        }
        return "";
    }

}