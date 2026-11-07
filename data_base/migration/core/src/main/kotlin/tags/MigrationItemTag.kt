package gog.my_project.data_base_migration.core.tags

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity

/** A migration item entry; consumers validate [value] through [MigrationIdentity]. */
interface MigrationItemTag : MigrationTag {
    val type: MigrationTagType
        get() = MigrationTagType.ITEM
}
