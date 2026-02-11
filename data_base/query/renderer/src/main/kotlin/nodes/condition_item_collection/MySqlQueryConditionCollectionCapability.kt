package gog.my_project.data_base.query.renderer.nodes.condition_item_collection

import gog.my_project.data_base.query.ast.interfaces.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.dialect.data_class.conditions_item_collection.QueryConditionCollectionData
import gog.my_project.data_base.query.dialect.nodes.condition_item_collection.IQueryConditionCollectionCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import kotlin.collections.withIndex

class MySqlQueryConditionCollectionCapability : IQueryConditionCollectionCapability {

    override fun render(
        ast: IQueryConditionsCollectionAst ,
        ctx: IRenderContext ,
        dataClass: QueryConditionCollectionData?
    ): String? {

        val paramsIn = ast?.paramsCollection;
        if (paramsIn != null){
            var paramInStr = " ( ";
            for ((index, paramName) in paramsIn.withIndex()){
                paramInStr += " :$paramName ";
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