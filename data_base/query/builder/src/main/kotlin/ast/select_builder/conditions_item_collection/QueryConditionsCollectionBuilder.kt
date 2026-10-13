package gog.my_project.data_base.query.builder.ast.select_builder.conditions_item_collection

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection.QueryConditionsCollectionAst
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import kotlin.collections.plusAssign

class QueryConditionsCollectionBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryConditionsCollectionAst = QueryConditionsCollectionAst(),
    private val paramName: String
) : IQueryConditionsCollectionApi {

    private var parameterBindingScope = ParameterBindingScope()

    internal constructor(
        params: MutableList<SqlParameter<*>>,
        ast: IQueryConditionsCollectionAst,
        paramName: String,
        parameterBindingScope: ParameterBindingScope,
    ) : this(params, ast, paramName) {
        this.parameterBindingScope = parameterBindingScope
    }

    override fun <T> addParam(
        paramValue: T
    ): IQueryConditionsCollectionApi
    {
        val paramName = "$paramName${this.ast.paramsCollection.size}"
        val parameter = parameterBindingScope.canonical(SqlParameter.of(paramName, paramValue))
        this.ast.paramsCollection.add(parameter)
        params += parameter
        return this;
    }

}
