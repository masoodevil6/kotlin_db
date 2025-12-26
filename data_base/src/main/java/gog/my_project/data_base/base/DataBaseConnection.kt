package gog.my_project.data_base.src.base

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DataBaseConnection {


    var connection : Connection? = null;


    init {
        connection = try {
            DriverManager.getConnection(
                DatabaseConfig.dbDomain + ":" + DatabaseConfig.dbPort.toString() + "/" + DatabaseConfig.dbName ,
                DatabaseConfig.dbUserName ,
                DatabaseConfig.dbPassword
            )
        }
        catch (e: SQLException) {
            throw RuntimeException("can not connect to database")
        }

    }


    /*fun fetch(query: String?, execute: (ResultSet?) -> Unit) {
        connection.use {conn->
            conn?.prepareStatement(query).use { stmt ->
                val resultExecute = stmt?.executeQuery();
                execute(resultExecute);
            }
        }
    }*/

}