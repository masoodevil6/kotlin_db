package gog.my_project.data_base.query.renderer.nodes.column_base

import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.dialect.data_class.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.nodes.column_base.IQueryColumnBaseCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnBaseCapability : IQueryColumnBaseCapability {

    override fun render(
        ast: IQueryColumnsBaseAst,
        ctx: IRenderContext,
        dataClass: QueryColumnBaseData?
    ): String? {

        val table = ast.table;
        val tableAlias = ast.tableAlias;
        val tableColumn = ast.column;

        val cte = ast.cte;
        val cteAlias = ast.cteAlias;
        val cteSelect = ast.select;

        var columnStr = ""
        if (tableColumn != null && table != null){
            if (tableAlias != "") {
                columnStr = " ${tableAlias}.";
            }
            columnStr += "${tableColumn} ";
        }

        if (cteSelect != null && cte != null){
            if (cteAlias != "") {
                columnStr = " ${cteAlias}.";
            }
            columnStr += "${cteSelect} ";
        }

        return columnStr;
    }

}