package gog.my_project.data_base_migration.core.tags

/** Stable numeric codes used by the system migration tag table. */
object MigrationTagTypeCodes {
    const val VERSION = 0
    const val ITEM = 1
    const val GROUP = 2
}

/** Domain category for a migration tag. Group tags are intentionally not persisted. */
enum class MigrationTagType(
    val code: Int,
    val isPersisted: Boolean,
) {
    VERSION(MigrationTagTypeCodes.VERSION, isPersisted = true),
    ITEM(MigrationTagTypeCodes.ITEM, isPersisted = true),
    GROUP(MigrationTagTypeCodes.GROUP, isPersisted = false),
}
