package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base_query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder

class SystemMigrationVersionUpdateQuery(
    private val id: Int,
    private val status: Int,
) {
    fun query(): IQueryRenderUpdateApi = QueryRenderUpdateBuilder()
        .table { table(SystemMigrationVersionModel::class) }
        .addValue {
            column(SystemMigrationVersionModel::class, SystemMigrationVersionModel::status)
            setValue {
                paramName("status")
                paramValue(status)
            }
        }
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
                        paramName("id")
                        paramValue(id)
                    }
                }
            }
        }
}
