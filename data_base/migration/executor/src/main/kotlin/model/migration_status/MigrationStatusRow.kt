package model.migration_status

/** One materialized row from the combined System Migration status CTE. */
data class MigrationStatusRow(
    val tagId: Int,
    val tagName: String,
    val tagType: Int,
    val tagVersionId: Int?,
    val migrationHistoryId: Int?,
    val appliedVersionReferenceId: Int?,
    val appliedVersionId: Int?,
    val appliedVersionTagId: Int?,
    val appliedVersionTagName: String?,
    val appliedVersionTagType: Int?,
)
