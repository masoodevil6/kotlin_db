package gog.my_project.data_base_migration.core.system_migration.model

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase

@QBTable(name = "system_migration_tag", alias = "smt")
internal class SystemMigrationTagModel : IModelBase {
    @QBColumn(name = "id", alias = "system_migration_tag_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "tagName", alias = "system_migration_tag_name")
    val tagName: String = ""

    @QBColumn(name = "tagType", alias = "system_migration_tag_type")
    val tagType: Int = 0

    @QBColumn(name = "created_at", alias = "system_migration_tag_created_at")
    val createdAt: String? = null

    companion object {
        const val TAG_TYPE_VERSION = 0
        const val TAG_TYPE_MIGRATION = 1
    }
}
