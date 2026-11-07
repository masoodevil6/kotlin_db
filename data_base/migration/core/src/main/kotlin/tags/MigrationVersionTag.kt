package gog.my_project.data_base_migration.core.tags

/** A named migration version with the existing system-table length constraint. */
interface MigrationVersionTag : MigrationTag {
    val validatedValue: String
        get() = validatedTagName(value, "Migration version tag", maxLength = 255)

    val type: MigrationTagType
        get() = MigrationTagType.VERSION
}
