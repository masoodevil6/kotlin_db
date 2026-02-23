package gog.my_project.data_base.query.renderer.nodes.select_nodes.table

import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.dialect.data_class.select_data.table.QueryTableData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.table.IQueryTableCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryTableCapability :
    IQueryTableCapability {

    override fun render(
        ast:         IQueryTableAst,
        ctx:         IRenderContext,
        dataClass:   QueryTableData?
    ): String? {

        val table: String? = ast.table;
        val tableAlias: String? = ast.tableAlias;

        val cte:   String? = ast.cte;
        val cteAlias:   String? = ast.cteAlias

        var queryTemp: String = "";
        if (table != null) {
            if (dataClass?._withPrefix == true){
                queryTemp = " ${ctx.dialect._prefixTable} ";
            }
            queryTemp += " ${table} ";
            if (tableAlias != "" && dataClass?._withAlias == true) {
                queryTemp += " As ${tableAlias} ";
            }
        }
        if (cte != null) {
            if (dataClass?._withPrefix == true){
                queryTemp = " FROM ";
            }
            queryTemp += " ${cte} ";
            if (cteAlias != "" && dataClass?._withAlias == true) {
                queryTemp += " As ${cteAlias} ";
            }
        }

        return queryTemp;
    }

}