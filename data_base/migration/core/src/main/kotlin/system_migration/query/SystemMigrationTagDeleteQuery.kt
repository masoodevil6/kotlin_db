package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationTagModel
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder

class SystemMigrationTagDeleteQuery(
    private val tagId: Int,
) {
    fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table(SystemMigrationTagModel::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(SystemMigrationTagModel::class)
                        columnSelector(SystemMigrationTagModel::id)
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("tag_id")
                        paramValue(tagId)
                    }
                }
            }
        }
}
