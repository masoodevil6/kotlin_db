package gog.my_project.data_base.core.data_base

import gog.my_project.data_base.core.query.dialect.DialectQuery

data class DatabaseConfig(
    val dbDomain    : String ,
    val dbPort      : Int?   ,
    val dbName      : String ,
    val dbUserName  : String ,
    val dbPassword  : String ,
    val dbPoolSize  : Int    = 10,
    val dialect: DialectQuery = DialectQuery.MY_SQL,
    val targetDatabaseVersion: ProductDatabaseVersion? = null,
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
