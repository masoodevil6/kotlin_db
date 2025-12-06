package gog.kotlin_db.utils

data class DatabaseConfig(
    val dbDomain    : String = "jdbc:mysql://localhost",
    val dbPort      : Int    = 3306,
    val dbName      : String = "kotlin_db",
    val dbUserName  : String = "root",
    val dbPassword  : String = "" ,
    val dbPoolSize  : Int    = 10
)
