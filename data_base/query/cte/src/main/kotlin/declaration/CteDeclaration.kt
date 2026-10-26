package gog.my_project.data_base.query.cte.declaration

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.metadata.queries.QueryMetadataResolver
import gog.my_project.data_base.query.cte.mapper.CteQueryRowMapper
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.collection.ICollection

/** Optional declaration base that builds a typed relation using its concrete runtime owner. */
abstract class CteDeclaration<P, Self : CteDeclaration<P, Self>> : ICteDeclaration<P> {
    abstract override fun createCte(params: P): Cte

    /** Creates a fresh typed output instance from one materialized row. */
    open fun fromQueryRow(row: QueryRow): Self = CteQueryRowMapper.map(this, row)

    /** Executes this declaration through the existing SELECT executor path. */
    open fun execute(
        queryManager: IQueryBuilderExecutor,
        params: P,
    ): ExecuteResult<ICollection<Self>> =
        configureConsumer(queryReady(queryManager, params)).executeAs(::fromQueryRow)

    /** Allows a declaration to configure callbacks on the prepared consumer. */
    protected open fun configureConsumer(consumer: QueryConsumer): QueryConsumer = consumer

    /** Builds a consumer for this invocation without starting SQL execution. */
    protected fun queryReady(
        queryManager: IQueryBuilderExecutor,
        params: P,
    ): QueryConsumer {
        val relation = createCte(params)
        val query = QueryRenderSelectBuilder(ast = relation.definition.ast)
        return queryManager.queryBuilder(query)
    }

    protected final fun buildCte(
        block: IQueryRenderSelectApi.() -> Unit,
    ): Cte = cte(
        name = QueryMetadataResolver.queryName(this::class),
        declarationOwner = this::class,
        block = block,
    )
}
