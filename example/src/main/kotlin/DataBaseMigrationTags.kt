package gog.my_project.example

import gog.my_project.data_base_manager.facade.MigrationTagDefinition
import gog.my_project.data_base_migration.core.tags.MigrationGroupTag
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

/**
 * String constants are declared once here. Kotlin does not automatically enumerate nested
 * `const val` members, so these properties expose references to the constants through the typed
 * Manager contract.
 */
object DataBaseMigrationTags : MigrationTagDefinition {

    enum class Versions(override val value: String) : MigrationVersionTag {
        Init("Init")
    }

    enum class Groups(override val value: String) : MigrationGroupTag {
        ANALYTICS("analytics")
    }

    enum class Items(override val value: String) : MigrationItemTag {
        MANAGER_CUSTOMER("create_manager_customers"),
        MANAGER_EVENTS("create_manager_events")
    }

    override val versions get() = Versions.entries
    override val groups get() = Groups.entries
    override val items get() = Items.entries
}
