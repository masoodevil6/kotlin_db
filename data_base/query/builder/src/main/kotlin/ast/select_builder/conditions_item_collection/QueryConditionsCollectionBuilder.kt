package gog.my_project.data_base.query.builder.ast.select_builder.conditions_item_collection

import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item_collection.IQueryConditionsCollectionApi
import gog.my_project.data_base.query.api.interfaces.api.parameter.IQueryParameterApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item_collection.QueryConditionsCollectionAst
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.parameter.QueryParameterBuilder
import kotlin.collections.plusAssign

class QueryConditionsCollectionBuilder(
    override var ast: IQueryConditionsCollectionAst = QueryConditionsCollectionAst(),
    private val legacyNamePrefix: String? = null,
) : IQueryConditionsCollectionApi {

    private var parameterBindingScope = ParameterBindingScope()

    internal constructor(
        ast: IQueryConditionsCollectionAst,
        legacyNamePrefix: String?,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast, legacyNamePrefix) {
        this.parameterBindingScope = parameterBindingScope
    }

    override fun addParam(
        blockParameter: IQueryParameterApi<Any?>.() -> Unit,
    ): IQueryConditionsCollectionApi {
        val parameterAst = QueryParameterAst<Any?>()
        val parameterBuilder = QueryParameterBuilder(parameterAst, parameterBindingScope::nextParameterName)
        parameterBuilder.apply(blockParameter)
        parameterBuilder.validate()
        val canonicalParameter = parameterBindingScope.canonical(parameterAst)
        // ---------------------------------------------------------------------
        // NEW PARAMETER AST IMPLEMENTATION
        // Each collection entry is the independently defined parameter AST node.
        // ---------------------------------------------------------------------
        ast.paramsCollection += canonicalParameter
        return this
    }




}
