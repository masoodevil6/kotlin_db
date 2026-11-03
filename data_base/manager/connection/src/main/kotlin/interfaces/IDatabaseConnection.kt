package gog.my_project.data_base_manager.connection.interfaces

import java.sql.Connection

interface IDatabaseConnection {

    fun build(): Connection

}