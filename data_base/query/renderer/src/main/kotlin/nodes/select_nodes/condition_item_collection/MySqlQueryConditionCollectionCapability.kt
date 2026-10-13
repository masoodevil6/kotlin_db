package gog.my_project.data_base.query.renderer.nodes.select_nodes.condition_item_collection

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item_collection.QueryConditionCollectionData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.condition_item_collection.IQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import kotlin.collections.withIndex

class MySqlQueryConditionCollectionCapability :
    IQueryConditionCollectionCapability {

    override fun render(
        ast:        IQueryConditionsCollectionAst,
        ctx:        IRenderContext,
        dataClass:  QueryConditionCollectionData?
    ): String? {

        val paramsIn = ast?.paramsCollection;
        if (paramsIn != null){
            var paramInStr = " ( ";
            val occurrences = mutableListOf<Any>()
            for ((index, param) in paramsIn.withIndex()){
                val paramName = when (param) {
                    is SqlParameter<*> -> {
                        occurrences += param
                        param.name
                    }
                    else -> param.toString()
                }
                paramInStr += " :$paramName ";
                if (index < paramsIn.size - 1){
                    paramInStr += ","
                }
            }
            paramInStr += " ) "
            ctx.parameterOccurrences?.addAll(occurrences)
            return paramInStr;
        }

        return null;

    }

}
