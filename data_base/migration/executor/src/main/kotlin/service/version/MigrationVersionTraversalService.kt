package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelection
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import model.prepared.PreparedMigrationVersion
import java.util.ArrayList
import java.util.Collections

internal enum class MigrationVersionTraversalDirection { UP, DOWN }

/** An immutable ordered instruction list containing no Scope, Connection, or callback. */
internal class MigrationVersionTraversalPlan(
    val direction: MigrationVersionTraversalDirection,
    versions: List<PreparedMigrationVersion>,
) {
    val versions: List<PreparedMigrationVersion> = Collections.unmodifiableList(ArrayList(versions))
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
