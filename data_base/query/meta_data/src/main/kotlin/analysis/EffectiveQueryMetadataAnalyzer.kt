package gog.my_project.data_base.query.meta_data.analysis

import gog.my_project.data_base.query.api.metadata.EffectiveOutputMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveRelationMetadata
import gog.my_project.data_base.query.ast.interfaces.select_interface.column.IQueryColumnsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.meta_data.ast.AstSelector

/** Builds effective SELECT metadata using only relationships and metadata recorded on the AST. */
class EffectiveQueryMetadataAnalyzer {
    private data class OutputSource(
        val tableName: String?,
        val tableAlias: String?,
    )

    private data class OutputSourceCandidate(
        val lookupAlias: String,
        val source: OutputSource,
    )

    fun analyze(ast: IQueryRenderSelectAst): EffectiveQueryMetadata {
        val withItems = AstSelector.from(ast)
            .select { it.withs }
            .selectMany { it.withs }
            .nodes

        val relations = withItems.map { item ->
            relationMetadata(
                logicalQueryName = item.metadata.logicalQueryName,
                cteRuntimeName = item.metadata.cteRuntimeName,
                withSqlName = item.metadata.withName,
                occurrenceAlias = null,
                definitionAst = item.withBody,
            )
        }.toMutableList()

        sourceOccurrence(ast, withItems)?.let(relations::add)

        val outputColumns = AstSelector.from(ast)
            .select { it.select }
            .selectMany { it.columns }
            .nodes

        return EffectiveQueryMetadata(
            relations = relations,
            outputs = outputColumns.map { column ->
                val source = outputSource(ast, column)
                EffectiveOutputMetadata(
                    logicalOutputName = column.metadata.logicalOutputName,
                    publishedSqlAlias = column.metadata.ColumnAlias,
                    executionType = column.metadata.ExecutionType,
                    tableName = source.tableName,
                    tableAlias = source.tableAlias,
                )
            },
        )
    }

    private fun sourceOccurrence(
        ast: IQueryRenderSelectAst,
        withItems: List<IQueryWithsItemAst>,
    ): EffectiveRelationMetadata? {
        val source = AstSelector.from(ast).select { it.table }.nodes.singleOrNull() ?: return null
        val metadata = source.metadata
        val cteRuntimeName = metadata.cteRuntimeName ?: return null
        val sourceName = metadata.resolveName()
        val definition = when {
            source.metadata.isCteObjectSource -> source.derivedQuery ?: withItems.firstOrNull { item ->
                item.metadata.cteRuntimeName == cteRuntimeName && item.metadata.withName == sourceName
            }?.withBody
            else -> withItems.firstOrNull { item -> item.metadata.withName == sourceName }?.withBody
        }

        return relationMetadata(
            logicalQueryName = metadata.logicalQueryName,
            cteRuntimeName = cteRuntimeName,
            withSqlName = if (metadata.isCteObjectSource) null else {
                withItems.firstOrNull { item -> item.metadata.withName == sourceName }?.metadata?.withName
            },
            occurrenceAlias = metadata.resolveAlias()?.takeIf { it.isNotBlank() },
            definitionAst = definition,
        )
    }

    private fun relationMetadata(
        logicalQueryName: String?,
        cteRuntimeName: String?,
        withSqlName: String?,
        occurrenceAlias: String?,
        definitionAst: IQueryRenderSelectAst?,
    ): EffectiveRelationMetadata = EffectiveRelationMetadata(
        logicalQueryName = logicalQueryName,
        cteRuntimeName = cteRuntimeName,
        withSqlName = withSqlName,
        occurrenceAlias = occurrenceAlias,
        outputs = definitionAst?.let(::outputColumns).orEmpty().map { column ->
            EffectiveOutputMetadata(
                logicalOutputName = column.metadata.logicalOutputName,
                publishedSqlAlias = column.metadata.ColumnAlias,
                executionType = column.metadata.ExecutionType,
            )
        },
    )

    private fun outputColumns(ast: IQueryRenderSelectAst): List<IQueryColumnsAst> =
        AstSelector.from(ast)
            .select { it.select }
            .selectMany { it.columns }
            .nodes

    private fun outputSource(ast: IQueryRenderSelectAst, column: IQueryColumnsAst): OutputSource {
        val columnAst = column.Column ?: return OutputSource(null, null)
        val referenceAlias = columnAst.metadata.resolveTableAlias()
            ?: return OutputSource(null, null)

        val candidates = buildList {
            addAll(AstSelector.from(ast).select { it.table }.nodes.map { outputSourceCandidate(it) })
            addAll(
                AstSelector.from(ast)
                    .select { it.joins }
                    .selectMany { it.joins }
                    .select { it.joinTable }
                    .nodes
                    .map { outputSourceCandidate(it) },
            )
        }.filter { it.lookupAlias == referenceAlias }

        val displayAlias = referenceAlias.takeIf { it.isNotBlank() }
        if (candidates.size != 1) return OutputSource(null, displayAlias)
        return candidates.single().source
    }

    private fun outputSourceCandidate(table: IQueryTableAst): OutputSourceCandidate {
        val resolvedAlias = table.metadata.resolveAlias()
        val lookupAlias = resolvedAlias ?: ""
        val sourceName = table.metadata.resolveName()
        val tableName = when {
            sourceName != null -> sourceName
            table.derivedQuery != null && table.metadata.isCteObjectSource -> table.metadata.cteRuntimeName
            else -> null
        }

        return OutputSourceCandidate(
            lookupAlias = lookupAlias,
            source = OutputSource(
                tableName = tableName,
                tableAlias = resolvedAlias?.takeIf { it.isNotBlank() },
            ),
        )
    }

}
