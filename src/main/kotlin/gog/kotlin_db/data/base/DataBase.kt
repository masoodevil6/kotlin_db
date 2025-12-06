package gog.kotlin_db.data.base

import gog.kotlin_db.utils.DatabaseConfig
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DataBase(

) {

    val config = DatabaseConfig();
    var connection : Connection? = null;

    init {
        connection = try {
            DriverManager.getConnection(
                config.dbDomain + ":" + config.dbPort.toString() + "/" + config.dbName ,
                config.dbUserName ,
                config.dbPassword
            )
        }
        catch (e: SQLException) {
            throw RuntimeException("can not connect to database")
        }
    }

}