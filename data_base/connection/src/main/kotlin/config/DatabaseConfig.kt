package gog.my_project.data_base.connection.config

data class DatabaseConfig(
    val dbDomain    : String ,
    val dbPort      : Int?   ,
    val dbName      : String ,
    val dbUserName  : String ,
    val dbPassword  : String ,
    val dbPoolSize  : Int    = 10
){

    fun getDbUrl(): String {
        var dbUrl: String = dbDomain;
        if (dbPort != null){
            dbUrl +=  ":" + dbPort.toString()
        }
        dbUrl +=  "/" + dbName

        return dbUrl;
    }
}