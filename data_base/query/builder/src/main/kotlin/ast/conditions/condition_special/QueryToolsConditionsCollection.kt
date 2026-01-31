package gog.my_project.data_base.query_builder.query.ast.conditions.condition_in

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.definition.interfaces.conditions.condition_special.IQueryDefinitionConditionCollection
import gog.my_project.data_base.query_builder.query.interfaces.conditions.condition_in.IQueryToolsConditionsCollection


open class QueryToolsConditionsCollection(
    override var params: MutableList<SqlParameter<*>>,
    private val paramName: String
)
    : IQueryToolsConditionsCollection , IQueryDefinitionConditionCollection
{

    private var paramsCollection: MutableList<Any> = mutableListOf()


    /* ==============================================================
    template
    ============================================================== */
    override fun getParamsCollection(): MutableList<Any> {
        return paramsCollection;
    }




    /* ==============================================================
    structure
    ============================================================== */
    override fun <T> addParam(paramValue: T): IQueryDefinitionConditionCollection {
        val paramName = "$paramName${paramsCollection.size}"
        paramsCollection.add(paramName);
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }

}