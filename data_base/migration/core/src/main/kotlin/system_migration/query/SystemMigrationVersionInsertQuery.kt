package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder

class SystemMigrationVersionInsertQuery(
    private val tagId: Int,
) {
    fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(SystemMigrationVersionModel::class) }
        .addValue {
            column(SystemMigrationVersionModel::class, SystemMigrationVersionModel::tagId)
            setValue {
                paramName("tag_id")
                paramValue(tagId)
            }
        }
}
