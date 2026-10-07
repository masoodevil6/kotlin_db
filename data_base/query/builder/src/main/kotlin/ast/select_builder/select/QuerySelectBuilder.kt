package gog.my_project.data_base.query.builder.ast.select_builder.select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.RelationSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.column.QueryColumnsBuilder

class QuerySelectBuilder internal constructor(
    override var params: MutableList<SqlParameter<*>>,
    override var ast: IQuerySelectAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val relationSourceContext: RelationSourceContext,
    private val relationDefinitionName: String?,
    private val relationDeclarationOwner: kotlin.reflect.KClass<*>?,
) : IQuerySelectApi {

    constructor(
        params: MutableList<SqlParameter<*>> = mutableListOf(),
        ast: IQuerySelectAst = QuerySelectAst(),
    ) : this(params, ast, ModelTableAliasContext(), RelationSourceContext(), null, null)

    override fun addColumn(
        blockColumn: IQueryColumnsApi.() -> Unit
    ): IQuerySelectApi
    {
        val ast = QueryColumnsAst();
        val builder = QueryColumnsBuilder(
            params,
            ast,
            modelAliasContext,
            relationSourceContext,
            relationDefinitionName,
            relationDeclarationOwner,
        ).apply(blockColumn)
        require(relationDefinitionName == null || builder.hasExplicitAlias) {
            "Every QueryRelation SELECT item must name its SQL output with alias(...)"
        }
        this.ast.columns.add(ast);
        return this;
    }

}
