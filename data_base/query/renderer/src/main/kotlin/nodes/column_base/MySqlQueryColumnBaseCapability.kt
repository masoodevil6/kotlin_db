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

        val tableAttribute = ast.tableAttribute;

        val tableAlias = ast.tableAlias;
        val column = ast.column;

        val cteAlias = ast.cteAlias;
        val select = ast.select;

        var columnStr = ""


        if (tableAttribute != null){
            columnStr += "${tableAttribute} ";

        }
        else if (column != null){
            if (tableAlias != "") {
                columnStr = " ${tableAlias}.";
            }
            columnStr += "${column} ";

        }
        else if (select != null){
            if (cteAlias != "") {
                columnStr = " ${cteAlias}.";
            }
            columnStr += "${select} ";

        }

        return columnStr;
    }

}