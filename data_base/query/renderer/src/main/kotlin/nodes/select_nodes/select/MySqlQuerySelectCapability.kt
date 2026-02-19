package gog.my_project.data_base.query.renderer.nodes.select_nodes.select

import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column.QueryColumnData
import gog.my_project.data_base.query.dialect.data_class.select_data.select.QuerySelectData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.select.IQuerySelectCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQuerySelectCapability :
    IQuerySelectCapability {

    override fun render(
        ast:         IQuerySelectAst,
        ctx:         IRenderContext,
        dataClass:   QuerySelectData?
    ): String? {

        val columnsList = ast.columns;
        if (columnsList.size > 0) {
            var selects = "";
            if (dataClass?._withPrefix == true){
                selects = " ${ctx.dialect._prefixSelect} "
            }
            for ((index, column) in columnsList.withIndex()){
                val itemColumn =
                    ctx.registry.render(
                        column ,
                        ctx.dialect ,
                        QueryColumnData(_withAlias = true)
                    );
                if (itemColumn != null){
                    selects +=  " $itemColumn";
                    if (index < columnsList.size - 1){
                        selects += ","
                    }
                }
            }

            return " $selects "
        }
        return "";

    }

}