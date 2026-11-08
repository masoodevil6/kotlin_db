package gog.my_project.data_base_migration.core.system_migration.model

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase

@QBTable(name = "system_migration_version", alias = "smv")
internal class SystemMigrationVersionModel : IModelBase {
    @QBColumn(name = "id", alias = "system_migration_version_id", primaryKey = true)
    val id: Int = 0

    @QBColumn(name = "tag_id", alias = "system_migration_version_tag_id")
    val tagId: Int = 0

    @QBColumn(name = "created_at", alias = "system_migration_version_created_at")
    val createdAt: String? = null
}
