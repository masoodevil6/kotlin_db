package gog.my_project.data_base.query.builder.relations

import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.queryRelation as buildQueryRelationPrimitive

/** Optional declaration base that builds a typed relation using its concrete runtime owner. */
abstract class QueryRelationDeclaration<P> : IQueryRelation<P> {
    abstract override val relationName: String

    abstract override fun queryRelation(params: P): QueryRelation

    protected final fun buildQueryRelation(
        block: IQueryRenderSelectApi.() -> Unit,
    ): QueryRelation = buildQueryRelationPrimitive(
        name = relationName,
        declarationOwner = this::class,
        block = block,
    )
}
