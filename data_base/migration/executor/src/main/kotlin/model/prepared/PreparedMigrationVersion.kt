package model.prepared

import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import java.util.ArrayList
import java.util.Collections

/** A selected Version and its migrations in their declared, deterministic order. */
internal class PreparedMigrationVersion(
    val version: MigrationVersion,
    migrations: List<RegisteredMigration>,
) {
    val migrations: List<RegisteredMigration> =
        Collections.unmodifiableList(ArrayList(migrations))
}
