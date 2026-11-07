package gog.my_project.data_base_migration.core.tags

/** An organizational group label; group tags are not stored in the system tag table. */
interface MigrationGroupTag : MigrationTag {
    val validatedValue: String
        get() = validatedTagName(value, "Migration group tag")

    val type: MigrationTagType
        get() = MigrationTagType.GROUP
}
