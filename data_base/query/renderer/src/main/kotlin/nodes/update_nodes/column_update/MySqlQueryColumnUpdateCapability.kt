package gog.my_project.data_base.query.renderer.nodes.update_nodes.column_update

import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.dialect.data_class.update_data.column_update.QueryColumnUpdateData
import gog.my_project.data_base.query.dialect.nodes.update_nodes.column_update.IQueryColumnUpdateCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryColumnUpdateCapability : IQueryColumnUpdateCapability {

    override fun render(
        ast: IQueryColumnUpdateAst,
        ctx: IRenderContext,
        dataClass: QueryColumnUpdateData?
    ): String? {
        val columnAlias = ast.columnAlias;
        val columnName = ast.columnName;
        val columnTag = ast.columnTag;

        if (dataClass?._getTag == true){
            return columnTag;
        }

        return  "${columnAlias}.${columnName}";
    }

}