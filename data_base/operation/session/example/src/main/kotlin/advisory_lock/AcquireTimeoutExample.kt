package gog.my_project.data_base_session.example.advisory_lock

import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection
import kotlin.time.Duration.Companion.seconds

internal object AcquireTimeoutExample {
    fun run(connection: Connection, dialect: SessionDialect, executor: SessionExecutor) {
        val namespace = "session-example"
        val name = "timeout-lock"
        val acquireAst = acquireSessionAdvisoryLock {
            resource(namespace, name)
            waitTimeout(5.seconds)
        }.ast

        when (executor.acquire(acquireAst, connection, dialect)) {
            AcquireSessionLockResult.Acquired -> {
                println("Acquire Timeout: acquired")
                val releaseAst = releaseSessionAdvisoryLock {
                    resource(namespace, name)
                }.ast
                when (executor.release(releaseAst, connection, dialect)) {
                    ReleaseSessionLockResult.Released -> println("Acquire Timeout: released")
                    ReleaseSessionLockResult.NotOwned -> println("Acquire Timeout: release reported NotOwned")
                }
            }

            AcquireSessionLockResult.TimedOut -> println("Acquire Timeout: timed out after 5 seconds")
        }
    }
}
