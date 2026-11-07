package gog.my_project.data_base_operation.config.data_base

/** Settings required to open one JDBC connection to a database. The password is redacted in [toString]. */
data class DatabaseConnectionConfig(
    val dbDomain: String,
    val dbPort: Int?,
    val dbName: String,
    val dbUserName: String,
    val dbPassword: String,
) {
    /** Returns useful connection diagnostics while always redacting the credential. */
    override fun toString(): String =
        "DatabaseConnectionConfig(dbDomain=$dbDomain, dbPort=$dbPort, dbName=$dbName, " +
            "dbUserName=$dbUserName, dbPassword=<redacted>)"

    fun getDbUrl(): String = buildString {
        append(dbDomain)
        if (dbPort != null) append(":").append(dbPort)
        append("/").append(dbName)
    }
}
