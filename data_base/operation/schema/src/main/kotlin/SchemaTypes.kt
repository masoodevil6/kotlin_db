package gog.my_project.data_base_schema

/** Public, implementation-independent column types accepted by the Schema facade. */
sealed interface SchemaDataType {
    object IntType : SchemaDataType
    object BooleanType : SchemaDataType
    object DateType : SchemaDataType
    object TextType : SchemaDataType
    object JsonType : SchemaDataType
    data class VarcharType(val length: Int) : SchemaDataType
    data class DecimalType(val precision: Int, val scale: Int) : SchemaDataType
    data class DateTimeType(val precision: Int? = null) : SchemaDataType
    data class TimestampType(val precision: Int? = null) : SchemaDataType
    data class TimeType(val precision: Int? = null) : SchemaDataType
}

/** Index access methods supported by the current Schema operation contract. */
enum class SchemaIndexMethod { BTREE, HASH }

/** Referential actions supported by the current Schema operation contract. */
enum class SchemaForeignKeyAction { CASCADE, RESTRICT, NO_ACTION, SET_NULL }

/** Dialects currently implemented by the Schema renderer. */
enum class SchemaDialect { MYSQL, MARIA_DB }

/**
 * A version is bound to a product so it cannot accidentally select a MySQL strategy for MariaDB
 * (or the reverse). Components are validated before reaching the renderer.
 */
data class SchemaVersion(
    val dialect: SchemaDialect,
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) {
            "Schema version components must be non-negative"
        }
    }
}
