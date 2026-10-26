package gog.my_project.data_base.query.example.v3.queries.relations

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration

@QBQuery(name = "user_page_query_v3", alias = "v3_user_page")
class UserPageQueryV3 : CteDeclaration<UserPageQueryV3.RelationFilters, UserPageQueryV3>() {
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
        from {
            cte(UserStatsQueryV3()(UserStatsQueryV3.RelationFilters()))
        }
        select {
            addColumn {
                column {
                    tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::id)
                }
                alias(UserPageQueryV3::id)
            }
            addColumn {
                column {
                    tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::name)
                }
                alias(UserPageQueryV3::name)
            }
            addColumn {
                column {
                    tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::family)
                }
                alias(UserPageQueryV3::family)
            }
            addColumn {
                column {
                    tableSelector(UserStatsQueryV3::class)
                    columnSelector(UserStatsQueryV3::age)
                }
                alias(UserPageQueryV3::age)
            }
        }
        limit {
            setOptionLimit(params.limit.toLong())
        }
        offset {
            setOptionOffset(params.offset.toLong())
        }
    }
}
