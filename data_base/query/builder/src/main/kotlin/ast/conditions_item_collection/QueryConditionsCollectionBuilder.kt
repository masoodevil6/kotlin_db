package gog.my_project.data_base.query.builder.ast.conditions_item_collection

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.ast.interfaces.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.conditions_item_collection.QueryConditionsCollectionAst
import kotlin.collections.plusAssign

class QueryConditionsCollectionBuilder(
    override var ast: IQueryConditionsCollectionAst = QueryConditionsCollectionAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    private val paramName: String
) : IQueryConditionsCollectionApi {


    override fun <T> addParam(
        paramValue: T
    ): IQueryConditionsCollectionApi
    {
        val paramName = "$paramName${this.ast.paramsCollection.size}"
        this.ast.paramsCollection.add(paramName);
        params plusAssign SqlParameter.Companion.of(paramName , paramValue)
        return this;
    }


}