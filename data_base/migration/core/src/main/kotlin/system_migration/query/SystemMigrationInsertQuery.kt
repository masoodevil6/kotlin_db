package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationModel
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder

class SystemMigrationInsertQuery(
    private val tagId: Int,
    private val versionId: Int,
) {
    fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(SystemMigrationModel::class) }
        .addValue {
            column(SystemMigrationModel::class, SystemMigrationModel::tagId)
            setValue {
                paramName("tag_id")
                paramValue(tagId)
            }
        }
        .addValue {
            column(SystemMigrationModel::class, SystemMigrationModel::versionId)
            setValue {
                paramName("version_id")
                paramValue(versionId)
            }
        }
}
