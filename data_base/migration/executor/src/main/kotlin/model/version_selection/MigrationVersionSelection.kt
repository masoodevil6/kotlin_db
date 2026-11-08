package gog.my_project.data_base_migration.executor.model.version_selection

import model.prepared.PreparedMigrationVersion
import java.util.ArrayList
import java.util.Collections

/** The direction in which the selected Migration Versions will be traversed. */
internal enum class MigrationVersionSelectionDirection {
    UP,
    DOWN,
}

/** An immutable, ordered selection of Migration Versions. */
internal class MigrationVersionSelection(
    val direction: MigrationVersionSelectionDirection,
    versions: List<PreparedMigrationVersion>,
) {
    val versions: List<PreparedMigrationVersion> =
        Collections.unmodifiableList(ArrayList(versions))
}
