package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

/**
 * Ordered category-specific migration tags accepted by one Manager.
 *
 * Kotlin interfaces cannot discover nested enum declarations on an implementing object. A
 * definition therefore exposes its enum entries through these three typed properties, typically
 * with `Versions.entries`, `Groups.entries`, and `Items.entries` getters.
 */
interface MigrationTagDefinition {
    val versions: List<MigrationVersionTag>
    val groups: List<MigrationGroupTag>
    val items: List<MigrationItemTag>
}
