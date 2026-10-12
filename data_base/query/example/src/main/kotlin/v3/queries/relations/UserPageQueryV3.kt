package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte

@QBQuery(name = "user_page_query_v3", alias = "v3_user_page")
class UserPageQueryV3(
    override val params: RelationFilters,
) : ICteDeclaration<UserPageQueryV3.RelationFilters> {
    data class RelationFilters(
        val limit: Int = 2,
        val offset: Int = 0,
    )

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "name", alias = "name")
    val name: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "family", alias = "family")
    val family: String? get() = error("Typed output property must not be read")
    @QBQuerySelect(name = "age", alias = "age")
    val age: Int? get() = error("Typed output property must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        from {
            cte(UserStatsQueryV3(UserStatsQueryV3.RelationFilters()))
        }
        select {
            addColumn { cteOutput(UserStatsQueryV3::id).alias(UserPageQueryV3::id) }
            addColumn { cteOutput(UserStatsQueryV3::name).alias(UserPageQueryV3::name) }
            addColumn { cteOutput(UserStatsQueryV3::family).alias(UserPageQueryV3::family) }
            addColumn { cteOutput(UserStatsQueryV3::age).alias(UserPageQueryV3::age) }
        }
        limit { setOptionLimit(params.limit.toLong()) }
        offset { setOptionOffset(params.offset.toLong()) }
    }
}
