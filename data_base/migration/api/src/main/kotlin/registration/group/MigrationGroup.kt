package gog.my_project.data_base_migration.api.registration.group

import gog.my_project.data_base_migration.api.registration.immutableSnapshot
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag

class MigrationGroup private constructor(
    name: String,
    registrations: List<MigrationNode>,
    /** The shared Core tag when this Group was registered through the tag-based DSL. */
    val tag: MigrationGroupTag?,
) : MigrationNode {
    val name: String = name.also {
        require(it.isNotBlank()) { "Migration group name must not be blank" }
    }

    val registrations: List<MigrationNode> = immutableSnapshot(registrations)

    /** Preserves the existing string-based registration API. */
    constructor(name: String, registrations: List<MigrationNode>) : this(name, registrations, null)

    companion object {
        internal fun fromTag(
            tag: MigrationGroupTag,
            registrations: List<MigrationNode>,
        ): MigrationGroup = MigrationGroup(tag.validatedValue, registrations, tag)
    }
}
