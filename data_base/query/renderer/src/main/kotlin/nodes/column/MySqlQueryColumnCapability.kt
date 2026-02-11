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
        val columnNameBase : IQueryColumnsBaseAst? = ast.ColumnName;
        val columnAlias :    String? = ast.ColumnAlias;

        val columnName =  ctx.registry.render(columnNameBase , ctx.dialect , QueryColumnBaseData() )
        if (columnName != null){
            var queryTemp: String = ""
            if (columnMethod != null) {
                queryTemp += columnMethod;
            }
            queryTemp += columnName;
            if (columnAlias != null) {
                queryTemp += " As ${columnAlias} ";
            }
            return queryTemp;
        }
        return null

    }


}