package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationTagModel
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder

class SystemMigrationTagInsertQuery(
    private val tagName: String,
    private val tagType: Int,
) {
    fun query(): IQueryRenderInsertApi = QueryRenderInsertBuilder()
        .table { table(SystemMigrationTagModel::class) }
        .addValue {
            column(SystemMigrationTagModel::class, SystemMigrationTagModel::tagName)
            setValue {
                paramName("tag_name")
                paramValue(tagName)
            }
        }
        .addValue {
            column(SystemMigrationTagModel::class, SystemMigrationTagModel::tagType)
            setValue {
                paramName("tag_type")
                paramValue(tagType)
            }
        }
}
