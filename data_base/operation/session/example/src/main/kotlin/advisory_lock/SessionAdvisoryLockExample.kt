package gog.my_project.data_base_session.example.advisory_lock

import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection

object SessionAdvisoryLockExample {
    fun run(connection: Connection, dialect: SessionDialect) {
        val executor = SessionExecutor()
        AcquireNoWaitExample.run(connection, dialect, executor)
        AcquireTimeoutExample.run(connection, dialect, executor)
        ReleaseNotOwnedExample.run(connection, dialect, executor)
    }
}
