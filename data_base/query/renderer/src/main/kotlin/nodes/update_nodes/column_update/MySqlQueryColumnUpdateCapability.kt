package gog.my_project.data_base.query.renderer.nodes.update_nodes.column_update

import gog.my_project.data_base.query.ast.interfaces.update_interface.column_update.IQueryColumnUpdateAst
import gog.my_project.data_base.query.dialect.data_class.update_data.column_update.QueryColumnUpdateData
import gog.my_project.data_base.query.dialect.nodes.update_nodes.column_update.IQueryColumnUpdateCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryColumnUpdateCapability : IQueryColumnUpdateCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast: IQueryColumnUpdateAst,
        ctx: IRenderContext,
        dataClass: QueryColumnUpdateData?
    ): String? {
        val columnAlias = ast.metadata.columnAlias;
        val columnName = ast.metadata.columnName;
        val columnTag = ast.metadata.columnTag;

        if (dataClass?._getTag == true){
            return columnTag;
        }

        return if (columnAlias == "") {
            columnName
        } else {
            "${columnAlias}.${columnName}"
        }
    }

}
