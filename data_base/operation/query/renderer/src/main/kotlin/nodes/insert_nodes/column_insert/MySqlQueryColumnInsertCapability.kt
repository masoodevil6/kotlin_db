package gog.my_project.data_base_query.renderer.nodes.insert_nodes.column_insert

import gog.my_project.data_base_query.ast.interfaces.insert_interface.columns_insert.IQueryColumnInsertAst
import gog.my_project.data_base_query.dialect.data_class.insert_data.column_insert.QueryColumnInsertData
import gog.my_project.data_base_query.dialect.nodes.insert_nodes.column_insert.IQueryColumnInsertCapability
import gog.my_project.data_base_query.renderer.interfaces.IRenderContext
import gog.my_project.data_base_query.renderer.interfaces.IParameterOccurrenceAwareRenderer

class MySqlQueryColumnInsertCapability : IQueryColumnInsertCapability, IParameterOccurrenceAwareRenderer {


    override fun render(
        ast:       IQueryColumnInsertAst,
        ctx:       IRenderContext,
        dataClass: QueryColumnInsertData?
    ): String? {

        val columnName = ast.metadata.columnName;
        val columnTag = ast.metadata.columnTag;

        if (dataClass?._getTag == true){
            return columnTag;
        }

        return  columnName;

    }


}
