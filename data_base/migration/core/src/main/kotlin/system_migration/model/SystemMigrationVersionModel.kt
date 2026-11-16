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

    @QBColumn(name = "status", alias = "system_migration_version_status")
    val status: Int = STATUS_MIGRATE_STARTED

    @QBColumn(name = "message", alias = "system_migration_version_message")
    val message: String? = null

    @QBColumn(name = "created_at", alias = "system_migration_version_created_at")
    val createdAt: String? = null

    companion object {
        const val STATUS_MIGRATE_STARTED = 0
        const val STATUS_MIGRATE_FAILED_UP = 1
        const val STATUS_MIGRATE_FAILED_RECORD = 2
        const val STATUS_MIGRATE_COMPLETED = 3

        const val STATUS_ROLLBACK_STARTED = 4
        const val STATUS_ROLLBACK_FAILED_DOWN = 5
        const val STATUS_ROLLBACK_FAILED_RECORD = 6
    }
}
