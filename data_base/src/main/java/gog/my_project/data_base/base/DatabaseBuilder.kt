package gog.my_project.data_base.base

import gog.my_project.data_base.src.base.DatabaseConfigBuilder
import gog.my_project.data_base.config.DatabaseConfig
import gog.my_project.data_base.config.DefaultDatabaseConfig
import gog.my_project.data_base.config.DialectQuery
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseBuilder {

    private var config:  DatabaseConfig = DefaultDatabaseConfig.config
    private var dialect: DialectQuery = DefaultDatabaseConfig.dialect;

    fun config(block: DatabaseConfigBuilder.() -> Unit): DatabaseBuilder {
        val builder = DatabaseConfigBuilder()
        builder.block()
        config = builder.build()
        return this
    }

    fun dialect(dialect: DialectQuery): DatabaseBuilder {
        this.dialect = dialect
        return this
    }

    fun getDialect(): DialectQuery = dialect

    fun build(): Connection {
        return try {
            DriverManager.getConnection(
                config.getDbUrl(),
                config.dbUserName ,
                config.dbPassword
            )
        }
        catch (e: SQLException) {
            throw RuntimeException("can not connect to database")
        }
    }


}