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
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.metadata.EffectiveOutputMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadataProvider
import gog.my_project.data_base.query.api.metadata.EffectiveRelationMetadata
import kotlin.reflect.KClass
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
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
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins.QueryJoinsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.meta_data.queries.QueryMetadataResolver
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
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
) : IQueryRenderSelectApi, EffectiveQueryMetadataProvider
{

    private data class OutputSource(
        val tableName: String?,
        val tableAlias: String?,
    )

    private data class OutputSourceCandidate(
        val lookupAlias: String?,
        val source: OutputSource,
    )

    internal var parameterBindingScope = ParameterBindingScope()
        private set

    internal constructor(
        params: MutableList<SqlParameter<*>>,
        ast: IQueryRenderSelectAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(params, ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    private val modelAliasContext = ModelTableAliasContext()
    private val querySourceContext = CteSourceContext()
    private var querySourceConfigured = false
    private var queryDefinitionName: String? = null
    private var queryDeclarationOwner: KClass<*>? = null
    private var explicitCteRoots: List<Cte> = emptyList()
    private var explicitWithItems: List<IQueryWithsItemAst> = emptyList()
    private var explicitCteNameOverrides = java.util.IdentityHashMap<Cte, String>()
    private var cteParamPrefixSize = 0

    override fun effectiveQueryMetadata(): EffectiveQueryMetadata {
        val sourceSnapshot = querySourceContext.snapshot()
        val knownRelations = orderedCteClosure(querySourceContext.dependencies() + explicitCteRoots)
        val withItems = ast.withs?.withs.orEmpty()
        val metadata = mutableListOf<EffectiveRelationMetadata>()

        knownRelations.forEach { relation ->
            val withItem = withItems.firstOrNull { item ->
                item.withName == effectiveCteName(relation)
            }
            metadata += relationMetadata(
                relation = relation,
                withSqlName = withItem?.withName,
                occurrenceAlias = null,
            )
        }

        withItems.forEach { item ->
            val knownRelation = knownRelations.firstOrNull { relation ->
                relation.name == item.withName || effectiveCteName(relation) == item.withName
            }
            if (knownRelation == null) {
                metadata += relationMetadata(
                    relation = null,
                    withSqlName = item.withName,
                    occurrenceAlias = null,
                    definitionAst = item.withBody,
                )
            }
        }

        if (sourceSnapshot != null) {
            metadata += relationMetadata(
                relation = sourceSnapshot.relation,
                withSqlName = null,
                occurrenceAlias = sourceSnapshot.effectiveAlias,
            )
        }

        val namedSource = ast.table?.cte
        if (sourceSnapshot == null && namedSource != null) {
            val namedSourceWithItem = withItems.firstOrNull { it.withName == namedSource }
            metadata += relationMetadata(
                relation = null,
                withSqlName = namedSourceWithItem?.withName,
                occurrenceAlias = ast.table?.cteAlias?.takeIf { it.isNotBlank() },
                cteRuntimeName = namedSource,
                definitionAst = namedSourceWithItem?.withBody,
            )
        }

        return EffectiveQueryMetadata(
            relations = metadata,
            outputs = ast.select?.columns.orEmpty().map { column ->
                val property = column.PropertyReference
                val source = outputSource(column)
                EffectiveOutputMetadata(
                    logicalOutputName = property?.let { QueryMetadataResolver.queryLogicalOutputName(it) },
                    publishedSqlAlias = column.ColumnAlias,
                    executionType = column.ExecutionType,
                    tableName = source.tableName,
                    tableAlias = source.tableAlias,
                )
            },
        )
    }

    private fun outputSource(column: IQueryColumnsAst): OutputSource {
        val columnAst = column.Column ?: return OutputSource(null, null)
        val referenceAlias = columnAst.cteAlias ?: columnAst.tableAlias
            ?: return OutputSource(null, null)
        val sourceSnapshot = querySourceContext.snapshot()
        val candidates = buildList {
            ast.table?.let { add(outputSourceCandidate(it, sourceSnapshot)) }
            ast.joins?.joins.orEmpty().forEach { join ->
                add(outputSourceCandidate(join.joinTable, null))
            }
        }.filter { it.lookupAlias == referenceAlias }

        val displayAlias = referenceAlias.takeIf { it.isNotBlank() }
        if (candidates.size != 1) return OutputSource(null, displayAlias)
        return candidates.single().source
    }

    private fun outputSourceCandidate(
        table: IQueryTableAst,
        sourceSnapshot: CteSourceContext.Snapshot?,
    ): OutputSourceCandidate {
        val lookupAlias = if (table.cte != null) table.cteAlias else table.tableAlias
        val tableName = when {
            table.cte != null -> table.cte
            table.table != null -> table.table
            table.derivedQuery != null && sourceSnapshot?.effectiveAlias == lookupAlias ->
                sourceSnapshot?.relation?.name
            else -> null
        }
        return OutputSourceCandidate(
            lookupAlias = lookupAlias,
            source = OutputSource(
                tableName = tableName,
                tableAlias = lookupAlias?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun relationMetadata(
        relation: Cte?,
        withSqlName: String?,
        occurrenceAlias: String?,
        cteRuntimeName: String? = relation?.name,
        definitionAst: IQueryRenderSelectAst? = relation?.definition?.ast,
    ): EffectiveRelationMetadata {
        val composition = relation as? BuiltCteComposition
        return EffectiveRelationMetadata(
            logicalQueryName = composition?.logicalQueryName,
            cteRuntimeName = cteRuntimeName,
            withSqlName = withSqlName,
            occurrenceAlias = occurrenceAlias,
            outputs = definitionAst?.select?.columns.orEmpty().map { column ->
                val property = column.PropertyReference
                EffectiveOutputMetadata(
                    logicalOutputName = property?.let { QueryMetadataResolver.queryLogicalOutputName(it) },
                    publishedSqlAlias = column.ColumnAlias,
                    executionType = column.ExecutionType,
                )
            },
        )
    }

    internal fun requireCteOutputAliases(name: String, declarationOwner: KClass<*>? = null) {
        queryDefinitionName = name
        queryDeclarationOwner = declarationOwner
    }

    internal fun cteDependencies(): List<Cte> = querySourceContext.dependencies()


    /* ==============================================================
    structure [withs]
    ============================================================== */

    override fun withs(
        blockWiths: IQueryWithsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        // Drop only the prefix inserted by the previous CTE composition pass.
        // The remaining parameters still belong to the existing query/legacy WITH blocks.
        if (cteParamPrefixSize > 0) {
            val nonCteParams = params.drop(cteParamPrefixSize)
            params.clear()
            params.addAll(nonCteParams)
            cteParamPrefixSize = 0
        }
        val ast = QueryWithsAst();
        val withsBuilder = QueryWithsBuilder(
            params,
            ast,
            parameterBindingScope,
        ).apply(blockWiths);
        explicitCteRoots = withsBuilder.cteRoots.toList()
        explicitWithItems = ast.withs.filterNot { item ->
            withsBuilder.cteRootItems.any { cteItem -> cteItem === item }
        }
        explicitCteNameOverrides = java.util.IdentityHashMap<Cte, String>().also {
            it.putAll(withsBuilder.cteNameOverrides)
        }
        this.ast.withs = ast;
        syncCteWithItems()
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
            querySourceContext,
            queryDefinitionName,
            queryDeclarationOwner,
        ).apply(blockSelect);
        this.ast.select = ast;
        return this;
    }

    internal fun addCteSource(
        relation: Cte,
        alias: String?,
    ): IQueryRenderSelectApi {
        check(!querySourceConfigured) { "Only one Cte source is supported in this version" }
        require(this.ast.table == null || (this.ast.table?.table == null && this.ast.table?.cte == null)) {
            "A Cte cannot be added when a table source is already configured"
        }
        require(relation.name.isNotBlank()) { "Cte name must not be blank" }

        val effectiveAlias = resolveCteOccurrenceAlias(relation, alias)

        querySourceContext.register(relation, effectiveAlias)
        val currentWiths = this.ast.withs ?: QueryWithsAst().also { this.ast.withs = it }
        syncCteWithItems(currentWiths)

        this.ast.table = QueryTableAst().apply {
            cte = effectiveCteName(relation)
            cteAlias = effectiveAlias
        }
        querySourceConfigured = true
        return this
    }

    internal fun addInlineCteSource(
        cte: Cte,
        alias: String?,
    ): IQueryRenderSelectApi {
        check(!querySourceConfigured) { "Only one Cte source is supported in this version" }
        require(this.ast.table == null || (this.ast.table?.table == null && this.ast.table?.cte == null)) {
            "A Cte cannot be added when a table source is already configured"
        }
        require(cte.name.isNotBlank()) { "Cte name must not be blank" }

        val effectiveAlias = resolveCteOccurrenceAlias(cte, alias)
        querySourceContext.registerInline(cte, effectiveAlias)
        this.ast.table = QueryTableAst().apply {
            derivedQuery = cte.definition.ast
            tableAlias = effectiveAlias
        }
        params.addAll(cte.definition.params)
        querySourceConfigured = true
        return this
    }

    private fun resolveCteOccurrenceAlias(
        cte: Cte,
        alias: String?,
    ): String {
        val queryOwner = (cte as? BuiltCteComposition)?.declarationOwner
        val defaultAlias = queryOwner?.let { QueryMetadataResolver.queryAlias(it) }
            ?: cte.name
        val effectiveAlias = alias ?: defaultAlias
        require(effectiveAlias.isNotBlank()) { "Cte alias must not be blank" }
        return effectiveAlias
    }

    private fun syncCteWithItems(
        withs: gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst? = this.ast.withs,
    ): List<Cte> {
        val roots = querySourceContext.dependencies() + explicitCteRoots
        val explicitNames = explicitWithItems.mapNotNull { it.withName }
        require(explicitNames.distinct().size == explicitNames.size) {
            "Duplicate CTE names are not allowed in a query"
        }
        val orderedRelations = orderedCteClosure(roots)
        val queryRuntimeNames = orderedRelations.map(::effectiveCteName)
        require((queryRuntimeNames + explicitNames).distinct().size == queryRuntimeNames.size + explicitNames.size) {
            "Duplicate CTE names are not allowed in a query"
        }
        if (withs != null) {
            withs.withs.clear()
            withs.withs.addAll(orderedRelations.map(::cteWithItem))
            withs.withs.addAll(explicitWithItems)
        }

        querySourceContext.dependencies().singleOrNull()?.let { activeCte ->
            this.ast.table?.cte = effectiveCteName(activeCte)
        }

        val composedCteParams = orderedRelations.flatMap(::ownCteParams)
        val nonCteParams = params.drop(cteParamPrefixSize)
        params.clear()
        params.addAll(composedCteParams)
        params.addAll(nonCteParams)
        cteParamPrefixSize = composedCteParams.size
        return orderedRelations
    }

    private fun orderedCteClosure(roots: List<Cte>): List<Cte> {
        val result = mutableListOf<Cte>()
        val visited = java.util.IdentityHashMap<Cte, Boolean>()
        val visiting = java.util.IdentityHashMap<Cte, Boolean>()
        val names = mutableSetOf<String>()

        fun visit(relation: Cte) {
            val effectiveName = effectiveCteName(relation)
            require(effectiveName.isNotBlank()) { "Cte name must not be blank" }
            check(!visiting.containsKey(relation)) { "Cte dependency cycle detected at $effectiveName" }
            if (visited.containsKey(relation)) return
            visiting[relation] = true
            relation.dependencies.forEach(::visit)
            visiting.remove(relation)
            require(names.add(effectiveName)) { "Duplicate CTE name $effectiveName" }
            visited[relation] = true
            result.add(relation)
        }

        roots.forEach(::visit)
        return result
    }

    private fun orderedCteClosure(root: Cte): List<Cte> = orderedCteClosure(listOf(root))

    private fun effectiveCteName(cte: Cte): String = explicitCteNameOverrides[cte] ?: cte.name

    private fun ownCteParams(relation: Cte): List<SqlParameter<*>> {
        val dependencyParamCount = relation.dependencies.sumOf { it.definition.params.size }
        require(relation.definition.params.size >= dependencyParamCount) {
            "Cte ${relation.name} parameter list does not contain its dependency parameters"
        }
        return relation.definition.params.drop(dependencyParamCount)
    }

    private fun cteWithItem(relation: Cte): IQueryWithsItemAst = QueryWithsItemAst().apply {
        withName = effectiveCteName(relation)
        val dependencyNames = relation.dependencies.flatMap { dependency ->
            orderedCteClosure(dependency).map { it.name }
        }.toSet()
        withBody = QueryRenderSelectAst().apply {
            val source = relation.definition.ast
            withs = source.withs?.let { sourceWiths ->
                QueryWithsAst().apply {
                    this.withs.addAll(sourceWiths.withs.filterNot { it.withName in dependencyNames })
                }
            }
            select = source.select
            table = source.table?.let { sourceTable ->
                val referencedCte = relation.dependencies.firstOrNull { it.name == sourceTable.cte }
                val effectiveDependencyName = referencedCte?.let(::effectiveCteName)
                if (effectiveDependencyName == null || effectiveDependencyName == sourceTable.cte) {
                    sourceTable
                } else {
                    QueryTableAst().apply {
                        table = sourceTable.table
                        tableAlias = sourceTable.tableAlias
                        cte = effectiveDependencyName
                        cteAlias = sourceTable.cteAlias
                        derivedQuery = sourceTable.derivedQuery
                    }
                }
            }
            joins = source.joins
            where = source.where
            optionLimit = source.optionLimit
            optionOffset = source.optionOffset
            optionGroup = source.optionGroup
            optionOrder = source.optionOrder
        }
    }



    /* ==============================================================
      structure [from]
      ============================================================== */
    override fun from(
        blockTable: gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        check(!querySourceConfigured) { "A Cte source is already configured" }
        val ast = QueryTableAst();
        val tableBuilder = QueryTableBuilder(
            params,
            ast,
            modelAliasContext,
            allowSelectQuerySources = true,
            parameterBindingScope = parameterBindingScope,
        ).apply(blockTable);
        val selectedCte = tableBuilder.selectedQueryRelation
        if (selectedCte != null) {
            return addInlineCteSource(selectedCte, tableBuilder.selectedOccurrenceAlias)
        }
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
            ast,
            parameterBindingScope,
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
            ast,
            parameterBindingScope,
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

/** Internal bridge for Builder-owned Cte composition after removing the public SELECT overload. */
internal fun IQueryRenderSelectApi.fromCteInternal(
    relation: Cte,
    alias: String? = null,
): IQueryRenderSelectApi {
    val builder = this as? QueryRenderSelectBuilder
        ?: error("Internal Cte composition requires QueryRenderSelectBuilder")
    return builder.addCteSource(relation, alias)
}
