package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.relations.buildQueryRelation

class UserPageRelationV3 : IQueryRelation<UserPageRelationV3.RelationFilters> {
    data class RelationFilters(
        val limit: Int = 2,
        val offset: Int = 0,
    )

    override val relationName = "v3_user_page"

    val id: Int get() = error("Typed output property must not be read")
    val name: String? get() = error("Typed output property must not be read")
    val family: String? get() = error("Typed output property must not be read")
    val age: Int? get() = error("Typed output property must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        from(UserStatsRelationV3().queryRelation(UserStatsRelationV3.RelationFilters()))
        select {
            addColumn { relationColumn(UserStatsRelationV3::id).alias(UserPageRelationV3::id) }
            addColumn { relationColumn(UserStatsRelationV3::name).alias(UserPageRelationV3::name) }
            addColumn { relationColumn(UserStatsRelationV3::family).alias(UserPageRelationV3::family) }
            addColumn { relationColumn(UserStatsRelationV3::age).alias(UserPageRelationV3::age) }
        }
        limit { setOptionLimit(params.limit.toLong()) }
        offset { setOptionOffset(params.offset.toLong()) }
    }
}
