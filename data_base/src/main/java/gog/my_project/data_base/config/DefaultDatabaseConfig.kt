package gog.my_project.data_base.config




internal object DefaultDatabaseConfig {

    val config = DatabaseConfig(
        dbDomain = "jdbc:mysql://127.0.0.1",
        dbPort = 3306,
        dbName = "",
        dbUserName = "root",
        dbPassword = "",
        dbPoolSize = 10
    )

    val dialect: DialectQuery = DialectQuery.MY_SQL

}
