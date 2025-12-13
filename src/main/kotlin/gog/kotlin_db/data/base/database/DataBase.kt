package gog.kotlin_db.data.base.database

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_template.ITemplateSqlType
import gog.kotlin_db.utils.params.ObjectSqlTypes
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

open class DataBase(

) {

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


    fun fetch(query: String?, execute: (ResultSet?) -> Unit) {
        connection.use {conn->
            conn?.prepareStatement(query).use { stmt ->
                val resultExecute = stmt?.executeQuery();
                execute(resultExecute);
            }
        }
    }

}