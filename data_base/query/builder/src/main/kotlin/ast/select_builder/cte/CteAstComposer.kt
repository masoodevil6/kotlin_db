package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.withs.QueryWithsBuilder
import gog.my_project.data_base.query.builder.metadata.queries.QueryMetadataResolver
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst as IWithItemAst

/** Owns CTE source materialization and the WITH AST derived from composed CTE values. */
internal class CteAstComposer(
    private val ast: IQueryRenderSelectAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val querySourceContext: CteSourceContext,
) {
    private data class NamedCteSource(
        val table: IQueryTableAst,
        val sqlName: String,
        val occurrenceAlias: String?,
    )

    private var explicitCteRoots: List<Cte> = emptyList()
    private var explicitCteRootItems: List<IWithItemAst> = emptyList()
    private var explicitWithEntries: List<IWithItemAst> = emptyList()
    private var explicitCteNameOverrides = java.util.IdentityHashMap<Cte, String>()
    private var registeredWithRelations: List<Cte> = emptyList()
    private var composedDependencies: List<Cte> = emptyList()
    private var namedCteSource: NamedCteSource? = null

    fun dependencies(): List<Cte> = composedDependencies.toList()

    fun setExplicitWiths(withsAst: IQueryWithsAst, builder: QueryWithsBuilder) {
        explicitCteRoots = builder.cteRoots.toList()
        explicitCteRootItems = builder.cteRootItems.toList()
        explicitWithEntries = withsAst.withs.toList()
        explicitCteNameOverrides = java.util.IdentityHashMap<Cte, String>().also {
            it.putAll(builder.cteNameOverrides)
        }
        syncCteWithItems()
        rebindNamedCteSource()
    }

    fun registerNamedCteSource(table: IQueryTableAst) {
        val metadata = table.metadata
        if (metadata.cteRuntimeName == null || metadata.isCteObjectSource) {
            clearNamedCteSource()
            return
        }
        val sqlName = metadata.resolveName() ?: return
        namedCteSource = NamedCteSource(table, sqlName, metadata.aliasFromAst)
        rebindNamedCteSource()
    }

    fun clearNamedCteSource() {
        namedCteSource?.let { source ->
            modelAliasContext.unregisterNamedSource(source.table)
            querySourceContext.clearNamedBinding()
        }
        namedCteSource = null
    }

    fun addCteSource(relation: Cte, alias: String?) {
        check(querySourceContext.snapshot() == null) { "Only one Cte source is supported in this version" }
        require(ast.table == null || ast.table?.metadata?.resolveName() == null) {
            "A Cte cannot be added when a table source is already configured"
        }
        require(relation.name.isNotBlank()) { "Cte name must not be blank" }

        val effectiveAlias = resolveCteOccurrenceAlias(relation, alias)
        val declarationOwner = (relation as? BuiltCteComposition)?.declarationOwner
        val aliasFromModel = if (declarationOwner == null) relation.name
        else QueryMetadataResolver.queryAliasFromModel(declarationOwner)
        val aliasFromAnnotation = declarationOwner?.let(QueryMetadataResolver::queryAliasFromAnnotation)

        querySourceContext.register(
            relation,
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            alias,
        )
        val currentWiths = ast.withs ?: QueryWithsAst().also { ast.withs = it }
        syncCteWithItems(currentWiths)

        ast.table = QueryTableAst().apply {
            metadata.nameFromAst = effectiveCteName(relation)
            metadata.aliasFromAst = effectiveAlias
            metadata.cteRuntimeName = relation.name
            metadata.logicalQueryName = (relation as? BuiltCteComposition)?.logicalQueryName
            metadata.isCteObjectSource = true
        }
        modelAliasContext.registerNamedSource(
            ast.table!!,
            effectiveCteName(relation),
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            alias,
        )
    }

    fun addInlineCteSource(cte: Cte, alias: String?) {
        check(querySourceContext.snapshot() == null) { "Only one Cte source is supported in this version" }
        require(ast.table == null || ast.table?.metadata?.resolveName() == null) {
            "A Cte cannot be added when a table source is already configured"
        }
        require(cte.name.isNotBlank()) { "Cte name must not be blank" }

        val effectiveAlias = resolveCteOccurrenceAlias(cte, alias)
        val declarationOwner = (cte as? BuiltCteComposition)?.declarationOwner
        val aliasFromModel = if (declarationOwner == null) cte.name
        else QueryMetadataResolver.queryAliasFromModel(declarationOwner)
        val aliasFromAnnotation = declarationOwner?.let(QueryMetadataResolver::queryAliasFromAnnotation)
        querySourceContext.registerInline(
            cte,
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            alias,
        )
        ast.table = QueryTableAst().apply {
            derivedQuery = cte.definition.ast
            metadata.aliasFromAst = effectiveAlias
            metadata.cteRuntimeName = cte.name
            metadata.logicalQueryName = (cte as? BuiltCteComposition)?.logicalQueryName
            metadata.isCteObjectSource = true
        }
        modelAliasContext.registerNamedSource(
            ast.table!!,
            effectiveAlias,
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            alias,
        )
    }

    private fun resolveCteOccurrenceAlias(cte: Cte, alias: String?): String {
        val queryOwner = (cte as? BuiltCteComposition)?.declarationOwner
        val defaultAlias = queryOwner?.let { QueryMetadataResolver.queryAlias(it) } ?: cte.name
        val effectiveAlias = alias ?: defaultAlias
        require(effectiveAlias.isNotBlank()) { "Cte alias must not be blank" }
        return effectiveAlias
    }

    private fun syncCteWithItems(
        withs: IQueryWithsAst? = ast.withs,
    ): List<Cte> {
        val cteByWithItem = java.util.IdentityHashMap<IWithItemAst, Cte>()
        explicitCteRootItems.zip(explicitCteRoots).forEach { (item, relation) ->
            cteByWithItem[item] = relation
        }

        val emittedRelations = java.util.IdentityHashMap<Cte, Boolean>()
        val composedWithItems = mutableListOf<IWithItemAst>()
        val orderedRelations = mutableListOf<Cte>()
        val orderedExplicitRelations = mutableListOf<Cte>()

        fun addByIdentity(target: MutableList<Cte>, relation: Cte) {
            if (target.none { it === relation }) target += relation
        }

        fun appendRelationClosure(root: Cte) {
            orderedCteClosure(root).forEach { relation ->
                if (emittedRelations.put(relation, true) == null) {
                    orderedRelations += relation
                    composedWithItems += cteWithItem(relation)
                }
            }
        }

        explicitWithEntries.forEach { item ->
            val relation = cteByWithItem[item]
            if (relation == null) composedWithItems += item
            else {
                orderedCteClosure(relation).forEach { addByIdentity(orderedExplicitRelations, it) }
                appendRelationClosure(relation)
            }
        }
        querySourceContext.dependencies().forEach(::appendRelationClosure)

        registeredWithRelations = orderedExplicitRelations.toList()
        val declaredDependencies = orderedExplicitRelations.toMutableList()
        querySourceContext.dependencies().forEach { addByIdentity(declaredDependencies, it) }
        composedDependencies = declaredDependencies.toList()

        if (withs != null) {
            withs.withs.clear()
            withs.withs.addAll(composedWithItems)
        }

        querySourceContext.dependencies().singleOrNull()?.let { activeCte ->
            ast.table?.metadata?.nameFromAst = effectiveCteName(activeCte)
        }

        return orderedRelations
    }

    private fun rebindNamedCteSource() {
        val source = namedCteSource ?: return
        val table = source.table
        modelAliasContext.unregisterNamedSource(table)
        querySourceContext.clearNamedBinding()

        // Restore the original raw cte(String) metadata before resolving against the latest WITH list.
        table.metadata.nameFromModel = null
        table.metadata.nameFromAnnotation = null
        table.metadata.nameFromAst = source.sqlName
        table.metadata.aliasFromModel = null
        table.metadata.aliasFromAnnotation = null
        table.metadata.aliasFromAst = source.occurrenceAlias
        table.metadata.cteRuntimeName = source.sqlName
        table.metadata.logicalQueryName = null
        table.metadata.isCteObjectSource = false

        val relationMatches = registeredWithRelations.filter { effectiveCteName(it) == source.sqlName }
        val rawNameMatches = ast.withs?.withs.orEmpty().count {
            it.metadata.withName == source.sqlName && it.metadata.cteRuntimeName == null
        }
        val totalMatches = relationMatches.size + rawNameMatches

        if (totalMatches > 1) {
            querySourceContext.markNamedSourceAmbiguous(
                "Named CTE source '${source.sqlName}' is ambiguous in WITH; typed references require a unique relation",
            )
            modelAliasContext.registerNamedSource(
                table,
                source.sqlName,
                source.occurrenceAlias ?: source.sqlName,
                aliasFromAst = source.occurrenceAlias,
            )
            return
        }

        val relation = relationMatches.singleOrNull()
        if (relation == null) {
            modelAliasContext.registerNamedSource(
                table,
                source.sqlName,
                source.occurrenceAlias ?: source.sqlName,
                aliasFromAst = source.occurrenceAlias,
            )
            return
        }

        val declarationOwner = (relation as? BuiltCteComposition)?.declarationOwner
        val aliasFromModel = if (declarationOwner == null) relation.name
        else QueryMetadataResolver.queryAliasFromModel(declarationOwner)
        val aliasFromAnnotation = declarationOwner?.let(QueryMetadataResolver::queryAliasFromAnnotation)
        val effectiveAlias = resolveCteOccurrenceAlias(relation, source.occurrenceAlias)

        table.metadata.nameFromAst = source.sqlName
        table.metadata.aliasFromAst = effectiveAlias
        table.metadata.cteRuntimeName = relation.name
        table.metadata.logicalQueryName = (relation as? BuiltCteComposition)?.logicalQueryName
        // Keep the AST's named-source provenance while attaching declaration identity metadata.
        table.metadata.isCteObjectSource = false
        querySourceContext.registerNamed(
            relation,
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            source.occurrenceAlias,
        )
        modelAliasContext.registerNamedSource(
            table,
            source.sqlName,
            effectiveAlias,
            aliasFromModel,
            aliasFromAnnotation,
            source.occurrenceAlias,
        )
    }

    private fun orderedCteClosure(roots: List<Cte>): List<Cte> {
        val result = mutableListOf<Cte>()
        val visited = java.util.IdentityHashMap<Cte, Boolean>()

        fun visit(relation: Cte) {
            val effectiveName = effectiveCteName(relation)
            require(effectiveName.isNotBlank()) { "Cte name must not be blank" }
            if (visited.put(relation, true) != null) return
            relation.dependencies.forEach(::visit)
            result.add(relation)
        }

        roots.forEach(::visit)
        return result
    }

    private fun orderedCteClosure(root: Cte): List<Cte> = orderedCteClosure(listOf(root))

    private fun effectiveCteName(cte: Cte): String = explicitCteNameOverrides[cte] ?: cte.name

    private fun cteWithItem(relation: Cte): IWithItemAst = QueryWithsItemAst().apply {
        metadata.withName = effectiveCteName(relation)
        metadata.cteRuntimeName = relation.name
        metadata.logicalQueryName = (relation as? BuiltCteComposition)?.logicalQueryName
        val dependencyRelations = mutableListOf<Cte>()
        val seenDependencyRelations = java.util.IdentityHashMap<Cte, Boolean>()
        relation.dependencies.forEach { dependency ->
            orderedCteClosure(dependency).forEach { transitiveDependency ->
                if (seenDependencyRelations.put(transitiveDependency, true) == null) {
                    dependencyRelations += transitiveDependency
                }
            }
        }
        val dependencyRuntimeNames = dependencyRelations.map { it.name }.toSet()
        val dependencyEffectiveNames = dependencyRelations.map(::effectiveCteName).toSet()
        withBody = QueryRenderSelectAst().apply {
            val source = relation.definition.ast
            withs = source.withs?.let { sourceWiths ->
                QueryWithsAst().apply {
                    this.withs.addAll(sourceWiths.withs.filterNot {
                        it.metadata.cteRuntimeName in dependencyRuntimeNames ||
                            it.metadata.withName in dependencyRuntimeNames ||
                            it.metadata.withName in dependencyEffectiveNames
                    })
                }
            }
            select = source.select
            table = source.table?.let { sourceTable ->
                val sourceName = sourceTable.metadata.resolveName()
                val sourceRuntimeName = sourceTable.metadata.cteRuntimeName
                val referencedCte = dependencyRelations.firstOrNull {
                    it.name == sourceRuntimeName || it.name == sourceName
                }
                val effectiveDependencyName = referencedCte?.let(::effectiveCteName)
                if (effectiveDependencyName == null || effectiveDependencyName == sourceName) {
                    sourceTable
                } else {
                    QueryTableAst().apply {
                        metadata.nameFromModel = sourceTable.metadata.nameFromModel
                        metadata.nameFromAnnotation = sourceTable.metadata.nameFromAnnotation
                        metadata.nameFromAst = effectiveDependencyName
                        metadata.aliasFromModel = sourceTable.metadata.aliasFromModel
                        metadata.aliasFromAnnotation = sourceTable.metadata.aliasFromAnnotation
                        metadata.aliasFromAst = sourceTable.metadata.aliasFromAst
                        metadata.cteRuntimeName = sourceTable.metadata.cteRuntimeName
                        metadata.logicalQueryName = sourceTable.metadata.logicalQueryName
                        metadata.isCteObjectSource = sourceTable.metadata.isCteObjectSource
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
}
