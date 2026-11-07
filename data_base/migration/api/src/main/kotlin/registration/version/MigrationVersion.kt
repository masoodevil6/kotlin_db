package gog.my_project.data_base_migration.api.registration.version

import gog.my_project.data_base_migration.api.registration.immutableSnapshot
import gog.my_project.data_base_migration.api.registration.group.MigrationNode
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

class MigrationVersion private constructor(
    name: String,
    registrations: List<MigrationNode>,
    /** The shared Core tag when this Version was registered through the tag-based DSL. */
    val tag: MigrationVersionTag?,
) : MigrationRegistration {
    val name: String = name.also {
        require(it.isNotBlank()) {
            "Migration version name must not be blank"
        }
    }

    val registrations: List<MigrationNode> =
        immutableSnapshot(registrations)

    /** Preserves the existing string-based registration API. */
    constructor(name: String, registrations: List<MigrationNode>) : this(name, registrations, null)

    companion object {
        internal fun fromTag(
            tag: MigrationVersionTag,
            registrations: List<MigrationNode>,
        ): MigrationVersion = MigrationVersion(tag.validatedValue, registrations, tag)
    }
}
