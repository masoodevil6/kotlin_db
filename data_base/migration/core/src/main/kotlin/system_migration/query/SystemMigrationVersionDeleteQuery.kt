package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder

class SystemMigrationVersionDeleteQuery(
    private val versionId: Int,
) {
    fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table(SystemMigrationVersionModel::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(SystemMigrationVersionModel::class)
                        columnSelector(SystemMigrationVersionModel::id)
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("system_migration_version_id")
                        paramValue(versionId)
                    }
                }
            }
        }
}
