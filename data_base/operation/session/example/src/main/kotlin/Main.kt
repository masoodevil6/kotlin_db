package gog.my_project.data_base_session.example

import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.example.advisory_lock.SessionAdvisoryLockExample

fun main() {
    DatabaseConnectionFactory.openMySqlConnection().use { connection ->
        SessionAdvisoryLockExample.run(connection, SessionDialect.MYSQL)
    }
}
