package gog.my_project.data_base_session.example.advisory_lock

import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection
import java.util.UUID

internal object ReleaseNotOwnedExample {
    fun run(connection: Connection, dialect: DatabaseDialect, executor: SessionExecutor) {
        val releaseAst = releaseSessionAdvisoryLock {
            resource("session-example", "not-owned-${UUID.randomUUID()}")
        }.ast

        when (executor.release(releaseAst, connection, dialect)) {
            ReleaseSessionLockResult.Released -> println("Release NotOwned: Released")
            ReleaseSessionLockResult.NotOwned -> println("Release NotOwned: NotOwned")
        }
    }
}
