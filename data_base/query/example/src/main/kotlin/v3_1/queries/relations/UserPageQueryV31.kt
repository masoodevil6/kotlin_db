package gog.my_project.data_base.query.example.v3_1.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration

@QBQuery(name = "user_page_query_v31", alias = "v31_user_page")
class UserPageQueryV31 : CteDeclaration<UserPageQueryV31.RelationFilters, UserPageQueryV31>() {
    data class RelationFilters(
        val limit: Int = 2,
        val offset: Int = 0,
    )

    private var mappedId: Int? = null

    @QBQuerySelect(name = "id", alias = "id")
    var id: Int
        get() = mappedId
            ?: throw IllegalStateException("Output property 'id' has not been mapped")
        set(value) {
            mappedId = value
        }

    @QBQuerySelect(name = "name", alias = "name")
    var name: String? = null

    @QBQuerySelect(name = "family", alias = "family")
    var family: String? = null

    @QBQuerySelect(name = "age", alias = "age")
    var age: Int? = null

    override fun createCte(params: RelationFilters): Cte = buildCte {
        from { cte(UserStatsQueryV31()(UserStatsQueryV31.RelationFilters())) }
        select {
            addColumn {
                column { tableSelector(UserStatsQueryV31::class); columnSelector(UserStatsQueryV31::id) }
                alias(UserPageQueryV31::id)
            }
            addColumn {
                column { tableSelector(UserStatsQueryV31::class); columnSelector(UserStatsQueryV31::name) }
                alias(UserPageQueryV31::name)
            }
            addColumn {
                column { tableSelector(UserStatsQueryV31::class); columnSelector(UserStatsQueryV31::family) }
                alias(UserPageQueryV31::family)
            }
            addColumn {
                column { tableSelector(UserStatsQueryV31::class); columnSelector(UserStatsQueryV31::age) }
                alias(UserPageQueryV31::age)
            }
        }
        limit { setOptionLimit(params.limit.toLong()) }
        offset { setOptionOffset(params.offset.toLong()) }
    }
}
