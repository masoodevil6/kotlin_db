package model.migration_status

import java.util.ArrayList
import java.util.Collections

/** Immutable, ordered snapshot of rows returned by the System Migration status CTE. */
class MigrationStatusSnapshot(rows: List<MigrationStatusRow>) {
    val rows: List<MigrationStatusRow> = Collections.unmodifiableList(ArrayList(rows))
}
