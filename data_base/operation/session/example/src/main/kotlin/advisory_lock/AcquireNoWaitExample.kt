package gog.my_project.data_base_session.example.advisory_lock

import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import gog.my_project.data_base_session.dialect.enums.SessionDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection

internal object AcquireNoWaitExample {
    fun run(connection: Connection, dialect: SessionDialect, executor: SessionExecutor) {
        val namespace = "session-example"
        val name = "no-wait-lock"
        val acquireAst = acquireSessionAdvisoryLock {
            resource(namespace, name)
            noWait()
        }.ast

        when (executor.acquire(acquireAst, connection, dialect)) {
            AcquireSessionLockResult.Acquired -> {
                println("Acquire NoWait: acquired")
                val releaseAst = releaseSessionAdvisoryLock {
                    resource(namespace, name)
                }.ast
                when (executor.release(releaseAst, connection, dialect)) {
                    ReleaseSessionLockResult.Released -> println("Acquire NoWait: released")
                    ReleaseSessionLockResult.NotOwned -> println("Acquire NoWait: release reported NotOwned")
                }
            }

            AcquireSessionLockResult.TimedOut -> println("Acquire NoWait: timed out (lock was already held)")
        }
    }
}
