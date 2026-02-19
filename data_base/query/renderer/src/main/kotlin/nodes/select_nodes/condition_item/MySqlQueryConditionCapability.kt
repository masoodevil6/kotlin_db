package gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item.QueryConditionsData
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item_collection.QueryConditionCollectionData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_item.IQueryConditionCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryConditionCapability :
    IQueryConditionCapability {

    override fun render(
        ast:       IQueryConditionsAst,
        ctx:       IRenderContext,
        dataClass: QueryConditionsData?
    ): String? {

        val conditionLogical = ast.conditionLogical;
        val conditionSideLeft = ast.conditionSideLeft;
        val conditionOperation: String? = ast.conditionOperation;
        val conditionSideRight = ast.conditionSideRight;

        val conditionSideLeftStr = ctx.registry.render(conditionSideLeft , ctx.dialect , QueryColumnBaseData());

        val conditionSideRightStr =
            when (conditionSideRight) {
                is IQueryColumnsBaseAst ->          ctx.registry.render(conditionSideRight , ctx.dialect , QueryColumnBaseData() );
                is IQueryConditionsCollectionAst -> ctx.registry.render(conditionSideRight , ctx.dialect , QueryConditionCollectionData() );
                is String -> conditionSideRight;
                else -> null;
            };

        if (conditionSideLeftStr != null && conditionSideRightStr != null) {
            var queryTemp = "";

            if (conditionLogical != null && dataClass?._hasLogical == true){
                queryTemp += conditionLogical
            }

            queryTemp += " $conditionSideLeftStr $conditionOperation $conditionSideRightStr  "
            return queryTemp;
        }

        return null;
    }



}