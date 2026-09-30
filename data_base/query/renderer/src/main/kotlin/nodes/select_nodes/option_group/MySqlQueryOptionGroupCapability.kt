package gog.my_project.data_base.query.renderer.nodes.select_nodes.option_group

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.option_group.QueryOptionGroupData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.option_group.IQueryOptionGroupCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryOptionGroupCapability:
    IQueryOptionGroupCapability {

    override fun render(
        ast:       IQueryOptionGroupAst,
        ctx:       IRenderContext,
        dataClass: QueryOptionGroupData?
    ): String? {

        val columnsList = ast.groupByList;
        if (columnsList.isNotEmpty()) {
            var optionGroupStr = "";
            for ((index, column) in columnsList.withIndex()){
                val columnStr =
                    ctx.registry.render(
                        column ,
                        ctx.dialect ,
                        QueryColumnBaseData()
                    );
                if (columnStr != null) {
                    optionGroupStr += " $columnStr"
                }
                if (index < columnsList.size - 1){
                    optionGroupStr += ","
                }
            }

            return " ${ctx.dialect._prefixGroup} $optionGroupStr "
        }

        return null;

    }

}