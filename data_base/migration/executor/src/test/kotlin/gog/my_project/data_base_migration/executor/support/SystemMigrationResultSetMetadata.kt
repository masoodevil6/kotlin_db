package gog.my_project.data_base_migration.executor.support

import java.lang.reflect.Proxy
import java.sql.ResultSetMetaData

/** Supplies the output labels produced by the system-migration CTEs to JDBC test doubles. */
internal fun systemMigrationResultSetMetadata(sql: String): ResultSetMetaData {
    val normalizedSql = sql.lowercase()
    val labels = when {
        ("system_migration_status_query" in normalizedSql ||
            ("system_migration_tag_query" in normalizedSql &&
                "system_migration_version_query" in normalizedSql &&
                "system_migration_query" in normalizedSql) ||
            ("tagversionid" in normalizedSql && "appliedversionreferenceid" in normalizedSql)) -> listOf(
            "tagId",
            "tagName",
            "tagType",
            "tagVersionId",
            "migrationHistoryId",
            "appliedVersionReferenceId",
            "appliedVersionId",
            "appliedVersionTagId",
            "appliedVersionTagName",
            "appliedVersionTagType",
        )
        "system_migration_version_query" in normalizedSql ||
            "system_migration_version" in normalizedSql -> listOf("id", "tagId")
        "system_migration_tag_query" in normalizedSql ||
            "system_migration_tag" in normalizedSql -> listOf("id", "tagName", "tagType")
        "system_migration_query" in normalizedSql ||
            ("system_migration" in normalizedSql && "system_migration_tag" !in normalizedSql) ->
            listOf("id", "tagId", "versionId")
        else -> emptyList()
    }

    return Proxy.newProxyInstance(
        ResultSetMetaData::class.java.classLoader,
        arrayOf(ResultSetMetaData::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getColumnCount" -> labels.size
            "getColumnLabel", "getColumnName" -> labels[(args[0] as Int) - 1]
            else -> defaultValue(method.returnType)
        }
    } as ResultSetMetaData
}

private fun defaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Double.TYPE -> 0.0
    java.lang.Float.TYPE -> 0f
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}
