package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.meta_data.queries.QueryMetadataResolver
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte as buildCtePrimitive

/** Optional declaration base that builds a typed relation using its concrete runtime owner. */
abstract class CteDeclaration<P>(
    final override val params: P,
) : ICteDeclaration<P> {
    abstract override fun createCte(params: P): Cte

    protected final fun buildCte(
        block: IQueryRenderSelectApi.() -> Unit,
    ): Cte = buildCtePrimitive(
        name = QueryMetadataResolver.queryName(this::class),
        declarationOwner = this::class,
        block = block,
    )
}
