package gog.my_project.data_base.query.builder.ast.select_builder.query_render_select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import kotlin.reflect.KClass
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_limit.QueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_offset.QueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins.QueryJoinsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.RelationSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.option_group.QueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_limit.QueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_offset.QueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_order.QueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.select.QuerySelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.where.QueryWhereBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.withs.QueryWithsBuilder

class QueryRenderSelectBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderSelectAst = QueryRenderSelectAst(),
) : IQueryRenderSelectApi
{

    private val modelAliasContext = ModelTableAliasContext()
    private val relationSourceContext = RelationSourceContext()
    private var relationFromConfigured = false
    private var relationDefinitionName: String? = null
    private var relationDeclarationOwner: KClass<*>? = null

    internal fun requireRelationOutputAliases(name: String, declarationOwner: KClass<*>? = null) {
        relationDefinitionName = name
        relationDeclarationOwner = declarationOwner
    }

    internal fun relationDependencies(): List<QueryRelation> = relationSourceContext.dependencies()


    /* ==============================================================
    structure [withs]
    ============================================================== */

    override fun withs(
        blockWiths: IQueryWithsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWithsAst();
        QueryWithsBuilder(
            params,
            ast
        ).apply(blockWiths);
        this.ast.withs = ast;
        syncRelationWithItems()
        return this;
    }



    /* ==============================================================
      structure [select]
      ============================================================== */
    override fun select(
        blockSelect: IQuerySelectApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QuerySelectAst();
        QuerySelectBuilder(
            params,
            ast,
            modelAliasContext,
            relationSourceContext,
            relationDefinitionName,
            relationDeclarationOwner,
        ).apply(blockSelect);
        this.ast.select = ast;
        return this;
    }

    override fun from(
        relation: QueryRelation,
        alias: String?,
    ): IQueryRenderSelectApi {
        check(!relationFromConfigured) { "Only one QueryRelation source is supported in this version" }
        require(this.ast.table == null || (this.ast.table?.table == null && this.ast.table?.cte == null)) {
            "A QueryRelation cannot be added when a table source is already configured"
        }
        require(relation.name.isNotBlank()) { "QueryRelation name must not be blank" }

        val effectiveAlias = alias ?: relation.name
        require(effectiveAlias.isNotBlank()) { "QueryRelation alias must not be blank" }

        relationSourceContext.register(relation, effectiveAlias)
        val currentWiths = this.ast.withs ?: QueryWithsAst().also { this.ast.withs = it }
        val orderedRelations = syncRelationWithItems(currentWiths, relation)
        params.addAll(0, orderedRelations.flatMap(::ownRelationParams))

        this.ast.table = QueryTableAst().apply {
            cte = relation.name
            cteAlias = effectiveAlias
        }
        relationFromConfigured = true
        return this
    }

    private fun syncRelationWithItems(
        withs: gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst? = this.ast.withs,
        rootRelation: QueryRelation? = null,
    ): List<QueryRelation> {
        val root = rootRelation ?: relationSourceContext.dependencies().firstOrNull()
        val explicitNames = withs?.withs.orEmpty().mapNotNull { it.withName }
        require(explicitNames.distinct().size == explicitNames.size) {
            "Duplicate CTE names are not allowed in a query"
        }
        if (root == null) return emptyList()
        val orderedRelations = orderedRelationClosure(root)
        val relationNames = orderedRelations.map { it.name }
        require((relationNames + explicitNames).distinct().size == relationNames.size + explicitNames.size) {
            "Duplicate CTE names are not allowed in a query"
        }
        if (withs != null) {
            withs.withs.removeAll { item -> relationNames.contains(item.withName) }
            withs.withs.addAll(0, orderedRelations.map(::relationWithItem))
        }
        return orderedRelations
    }

    private fun orderedRelationClosure(root: QueryRelation): List<QueryRelation> {
        val result = mutableListOf<QueryRelation>()
        val visited = java.util.IdentityHashMap<QueryRelation, Boolean>()
        val visiting = java.util.IdentityHashMap<QueryRelation, Boolean>()
        val names = mutableSetOf<String>()

        fun visit(relation: QueryRelation) {
            require(relation.name.isNotBlank()) { "QueryRelation name must not be blank" }
            check(!visiting.containsKey(relation)) { "QueryRelation dependency cycle detected at ${relation.name}" }
            if (visited.containsKey(relation)) return
            visiting[relation] = true
            relation.dependencies.forEach(::visit)
            visiting.remove(relation)
            require(names.add(relation.name)) { "Duplicate QueryRelation CTE name ${relation.name}" }
            visited[relation] = true
            result.add(relation)
        }

        visit(root)
        return result
    }

    private fun ownRelationParams(relation: QueryRelation): List<SqlParameter<*>> {
        val dependencyParamCount = relation.dependencies.sumOf { it.definition.params.size }
        require(relation.definition.params.size >= dependencyParamCount) {
            "QueryRelation ${relation.name} parameter list does not contain its dependency parameters"
        }
        return relation.definition.params.drop(dependencyParamCount)
    }

    private fun relationWithItem(relation: QueryRelation): IQueryWithsItemAst = QueryWithsItemAst().apply {
        withName = relation.name
        val dependencyNames = relation.dependencies.flatMap { dependency ->
            orderedRelationClosure(dependency).map { it.name }
        }.toSet()
        withBody = QueryRenderSelectAst().apply {
            val source = relation.definition.ast
            withs = source.withs?.let { sourceWiths ->
                QueryWithsAst().apply {
                    this.withs.addAll(sourceWiths.withs.filterNot { it.withName in dependencyNames })
                }
            }
            select = source.select
            table = source.table
            joins = source.joins
            where = source.where
            optionLimit = source.optionLimit
            optionOffset = source.optionOffset
            optionGroup = source.optionGroup
            optionOrder = source.optionOrder
        }
    }



    /* ==============================================================
      structure [table]
      ============================================================== */
    override fun table(
        blockTable: gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        check(!relationFromConfigured) { "A QueryRelation source is already configured" }
        val ast = QueryTableAst();
        QueryTableBuilder(
            params,
            ast,
            modelAliasContext,
        ).apply(blockTable);
        this.ast.table = ast;
        return this;
    }



    /* ==============================================================
      structure [joins]
      ============================================================== */
    override fun joins(
        blockJoins: IQueryJoinsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryJoinsAst();
        QueryJoinsBuilder(
            params,
            ast
        ).apply(blockJoins);
        this.ast.joins = ast;
        return this;
    }




    /* ==============================================================
      structure [where]
      ============================================================== */
    override fun where(
        blockGroup: IQueryWhereApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWhereAst();
        QueryWhereBuilder(
            params,
            ast
        ).apply(blockGroup);
        this.ast.where = ast;
        return this;
    }



    /* ==============================================================
      structure [options]
      ============================================================== */
    override fun limit(
        blockLimit: IQueryOptionLimitApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionLimitAst();
        QueryOptionLimitBuilder(
            params,
            ast
        ).apply(blockLimit)
        this.ast.optionLimit = ast;
        return this;
    }


    override fun offset(
        blockOffset: IQueryOptionOffsetApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOffsetAst();
        QueryOptionOffsetBuilder(
            params,
            ast
        ).apply(blockOffset);
        this.ast.optionOffset = ast;
        return this;
    }


    override fun group(
        blockGroup: IQueryOptionGroupApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        var ast = QueryOptionGroupAst();
        QueryOptionGroupBuilder(
            params,
            ast
        ).apply(blockGroup);
        this.ast.optionGroup = ast;
        return this;
    }


    override fun order(
        blockOrder: IQueryOptionOrderApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOrderAst();
        QueryOptionOrderBuilder(
            params,
            ast
        ).apply(blockOrder);
        this.ast.optionOrder = ast;
        return this;
    }


}
