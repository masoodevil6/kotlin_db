package gog.my_project.data_base.query.renderer.nodes.column

import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.dialect.data_class.column.QueryColumnData
import gog.my_project.data_base.query.dialect.data_class.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.nodes.column.IQueryColumnCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnCapability : IQueryColumnCapability {

    override fun render(
        ast: IQueryColumnsAst,
        ctx: IRenderContext ,
        dataClass: QueryColumnData?
    ): String? {
        val columnMethod :   String? = ast.ColumnMethod;
        val columnBase : IQueryColumnsBaseAst? = ast.Column;
        val columnAlias :    String? = ast.ColumnAlias;

        val column =  ctx.registry.render(
            columnBase ,
            ctx.dialect ,
            QueryColumnBaseData()
        )
        if (column != null){
            var queryTemp: String = ""
            if (columnMethod != null) {
                queryTemp += "($columnMethod)";
            }
            queryTemp += column;
            if (columnAlias != null) {
                queryTemp += " As ${columnAlias} ";
            }
            return queryTemp;
        }
        return null

    }


}