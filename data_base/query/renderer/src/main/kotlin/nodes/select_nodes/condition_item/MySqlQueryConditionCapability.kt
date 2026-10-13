package gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item

import gog.my_project.data_base.core.query.reader.SqlParameter
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

        val occurrenceStart = ctx.parameterOccurrences?.size
        val conditionSideLeftStr = ctx.registry.render(conditionSideLeft , ctx.dialect , QueryColumnBaseData());
        if (conditionSideLeftStr == null) {
            rollbackOccurrences(ctx, occurrenceStart)
            return null
        }

        var parameterOccurrence: SqlParameter<*>? = null
        val conditionSideRightStr =
            when (conditionSideRight) {
                is IQueryColumnsBaseAst ->          ctx.registry.render(conditionSideRight , ctx.dialect , QueryColumnBaseData() );
                is IQueryConditionsCollectionAst -> ctx.registry.render(conditionSideRight , ctx.dialect , QueryConditionCollectionData() );
                is SqlParameter<*> -> {
                    parameterOccurrence = conditionSideRight
                    ":${conditionSideRight.name}"
                }
                is String -> conditionSideRight;
                else -> null;
            };

        if (conditionSideRightStr != null) {
            var queryTemp = "";

            if (conditionLogical != null && dataClass?._hasLogical == true){
                queryTemp += conditionLogical
            }

            queryTemp += " $conditionSideLeftStr $conditionOperation $conditionSideRightStr  "
            parameterOccurrence?.let { ctx.parameterOccurrences?.add(it) }
            return queryTemp;
        }

        // A nested renderer may have tentatively registered occurrences before returning null.
        rollbackOccurrences(ctx, occurrenceStart)

        return null;
    }

    private fun rollbackOccurrences(ctx: IRenderContext, occurrenceStart: Int?) {
        ctx.parameterOccurrences?.let { occurrences ->
            while (occurrenceStart != null && occurrences.size > occurrenceStart) {
                occurrences.removeAt(occurrences.lastIndex)
            }
        }
    }



}
