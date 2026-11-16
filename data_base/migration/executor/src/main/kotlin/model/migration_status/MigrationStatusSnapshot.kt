package model.migration_status

import model.version_history.MigrationHistoryVersion
import java.util.ArrayList
import java.util.Collections

/** Immutable, ordered snapshot of rows returned by the System Migration status CTE. */
internal class MigrationStatusSnapshot private constructor(
    rows: List<MigrationStatusRow>,
    historyVersions: List<MigrationHistoryVersion>,
) {
    constructor(rows: List<MigrationStatusRow>) : this(rows, emptyList())

    val rows: List<MigrationStatusRow> = Collections.unmodifiableList(ArrayList(rows))
    internal val historyVersions: List<MigrationHistoryVersion> =
        Collections.unmodifiableList(ArrayList(historyVersions.sortedBy(MigrationHistoryVersion::id)))

    internal companion object {
        fun withHistoryVersions(
            rows: List<MigrationStatusRow>,
            historyVersions: List<MigrationHistoryVersion>,
        ): MigrationStatusSnapshot = MigrationStatusSnapshot(rows, historyVersions)
    }
}
