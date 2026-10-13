package gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item_collection

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item_collection.QueryConditionCollectionData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_item_collection.IQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.renderParameter

class MySqlQueryConditionCollectionCapability :
    IQueryConditionCollectionCapability, IParameterOccurrenceAwareRenderer {

    override fun render(
        ast:        IQueryConditionsCollectionAst,
        ctx:        IRenderContext,
        dataClass:  QueryConditionCollectionData?
    ): String? {

        val paramsIn = ast?.paramsCollection;
        if (paramsIn != null){
            var paramInStr = " ( ";
            for ((index, param) in paramsIn.withIndex()){
                val placeholder = when (param) {
                    is SqlParameter<*> -> ctx.renderParameter(param)
                    else -> ":${param}"
                }
                paramInStr += " $placeholder ";
                if (index < paramsIn.size - 1){
                    paramInStr += ","
                }
            }
            paramInStr += " ) "
            return paramInStr;
        }

        return null;

    }

}
