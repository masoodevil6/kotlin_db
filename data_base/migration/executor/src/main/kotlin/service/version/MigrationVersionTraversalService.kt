package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelection
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import gog.my_project.data_base_migration.executor.service.run.PreparedMigration
import model.prepared.PreparedMigrationVersion
import java.util.ArrayList
import java.util.Collections

internal enum class MigrationVersionTraversalDirection { UP, DOWN }

/** An immutable ordered instruction list, optionally completed with the runtime instances to execute. */
internal class MigrationVersionTraversalPlan(
    val direction: MigrationVersionTraversalDirection,
    versions: List<PreparedMigrationVersion>,
    preparedMigrationsByVersion: List<List<PreparedMigration>>? = null,
) {
    val versions: List<PreparedMigrationVersion> = Collections.unmodifiableList(ArrayList(versions))

    /** Runtime instances aligned with [versions] and each Version's declared Migration order. */
    val preparedMigrationsByVersion: List<List<PreparedMigration>>? =
        preparedMigrationsByVersion?.let { preparedByVersion ->
            require(preparedByVersion.size == this.versions.size) {
                "Prepared Migration lists must match the number of selected Versions"
            }
            preparedByVersion.forEachIndexed { versionIndex, preparedMigrations ->
                val registrations = this.versions[versionIndex].migrations
                require(preparedMigrations.size == registrations.size) {
                    "Prepared Migration count does not match Version '${this.versions[versionIndex].version.name}'"
                }
                preparedMigrations.forEachIndexed { migrationIndex, preparedMigration ->
                    require(preparedMigration.registration === registrations[migrationIndex]) {
                        "Prepared Migration order or Registration does not match Version " +
                            "'${this.versions[versionIndex].version.name}'"
                    }
                }
            }
            Collections.unmodifiableList(
                preparedByVersion.map { Collections.unmodifiableList(ArrayList(it)) },
            )
        }

    /** Returns this same traversal plan completed with the already-constructed Migration instances. */
    fun withPreparedMigrations(
        preparedMigrationsByVersion: List<List<PreparedMigration>>,
    ): MigrationVersionTraversalPlan = MigrationVersionTraversalPlan(
        direction = direction,
        versions = versions,
        preparedMigrationsByVersion = preparedMigrationsByVersion,
    )
}

/** Converts an already-selected Version list into the immutable traversal plan. */
internal class MigrationVersionTraversalService {
    fun plan(selection: MigrationVersionSelection): MigrationVersionTraversalPlan =
        MigrationVersionTraversalPlan(
            direction = when (selection.direction) {
                MigrationVersionSelectionDirection.UP -> MigrationVersionTraversalDirection.UP
                MigrationVersionSelectionDirection.DOWN -> MigrationVersionTraversalDirection.DOWN
            },
            versions = selection.versions,
        )
}
