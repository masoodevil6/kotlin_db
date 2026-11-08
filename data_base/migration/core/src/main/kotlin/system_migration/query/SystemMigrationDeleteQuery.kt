package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationModel
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder

class SystemMigrationDeleteQuery(
    private val tagId: Int,
    private val versionId: Int,
) {
    fun query(): IQueryRenderDeleteApi = QueryRenderDeleteBuilder()
        .table { table(SystemMigrationModel::class) }
        .where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(SystemMigrationModel::class)
                        columnSelector(SystemMigrationModel::tagId)
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("tag_id")
                        paramValue(tagId)
                    }
                }
                addCondition {
                    logicalAnd()
                    sideSelector {
                        tableSelector(SystemMigrationModel::class)
                        columnSelector(SystemMigrationModel::versionId)
                    }
                    operationEqual()
                    sideValueParam {
                        paramName("version_id")
                        paramValue(versionId)
                    }
                }
            }
        }
}
