package gog.my_project.data_base.query.builder.ast.select_builder.conditions_item_collection

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection.QueryConditionsCollectionAst
import kotlin.collections.plusAssign

class QueryConditionsCollectionBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryConditionsCollectionAst = QueryConditionsCollectionAst(),
    private val paramName: String
) : IQueryConditionsCollectionApi {

    override fun <T> addParam(
        paramValue: T
    ): IQueryConditionsCollectionApi
    {
        val paramName = "$paramName${this.ast.paramsCollection.size}"
        this.ast.paramsCollection.add(paramName);
        params += SqlParameter.of(paramName , paramValue)
        return this;
    }

}