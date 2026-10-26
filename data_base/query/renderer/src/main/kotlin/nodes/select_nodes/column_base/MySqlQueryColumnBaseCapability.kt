package gog.my_project.data_base.query.renderer.nodes.select_nodes.column_base

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.column_base.IQueryColumnBaseCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryColumnBaseCapability :
    IQueryColumnBaseCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast:        IQueryColumnsBaseAst,
        ctx:        IRenderContext,
        dataClass:  QueryColumnBaseData?
    ): String? {


        val tableAlias = ast.metadata.resolveTableAlias()
        val column = ast.metadata.resolveColumnName()

        var columnStr = ""

        if (column != null) {
            if (!tableAlias.isNullOrEmpty()) {
                columnStr = " $tableAlias."
            }
            columnStr += "$column "
        }

        return columnStr;
    }

}
