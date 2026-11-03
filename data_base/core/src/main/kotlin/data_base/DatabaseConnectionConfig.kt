package gog.my_project.data_base.core.data_base

/** Settings required to open one JDBC connection to a database. */
data class DatabaseConnectionConfig(
    val dbDomain: String,
    val dbPort: Int?,
    val dbName: String,
    val dbUserName: String,
    val dbPassword: String,
) {
    fun getDbUrl(): String = buildString {
        append(dbDomain)
        if (dbPort != null) append(":").append(dbPort)
        append("/").append(dbName)
    }
}
