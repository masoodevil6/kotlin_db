package gog.my_project.data_base_migration.core.migrate

import gog.my_project.data_base_migration.core.tags.MigrationItemTag

/** A migration whose validated identity is fixed by its concrete class declaration. */
abstract class Migration(item: MigrationItemTag) {
    val identity: MigrationIdentity = MigrationIdentity.fromValue(item.value)

    abstract fun up(): MigrationDefinition

    abstract fun down(): MigrationDefinition
}
