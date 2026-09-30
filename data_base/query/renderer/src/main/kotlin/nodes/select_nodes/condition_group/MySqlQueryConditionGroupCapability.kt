package gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_group

import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item.QueryConditionsData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_group.IQueryConditionGroupCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryConditionGroupCapability :
    IQueryConditionGroupCapability {

    override fun render(
        ast:       IQueryConditionsGroupsAst,
        ctx:       IRenderContext,
        dataClass: QueryConditionsGroupsData?
    ): String? {

        val groups = ast.conditions;
        val groupLogical = if (dataClass?._hasLogical == false && ast.conditionLogical != null){ast.conditionLogical}else{""}

        if (groups.isNotEmpty()) {

            var queryTemp = " $groupLogical  (";

            for ((index, condition) in groups.withIndex()){

                var conditionString : String? = null;
                if (condition is IQueryConditionsAst){
                    val conditionsData = QueryConditionsData();
                    conditionsData._hasLogical = false;
                    if (index > 0 ){
                        conditionsData._hasLogical = true;
                    }

                    conditionString = ctx.registry.render(condition , ctx.dialect , conditionsData)
                }
                else if (condition is IQueryConditionsGroupsAst){
                    val conditionsData = QueryConditionsGroupsData();
                    conditionsData._hasLogical = false;
                    if (index > 0 ){
                        conditionsData._hasLogical = true;
                    }

                    conditionString = ctx.registry.render(condition , ctx.dialect , conditionsData)
                }

                if (conditionString != null){
                    queryTemp +=  " $conditionString ";
                }
            }

            queryTemp += " ) ";

            return queryTemp;
        }

        return null;
    }

}