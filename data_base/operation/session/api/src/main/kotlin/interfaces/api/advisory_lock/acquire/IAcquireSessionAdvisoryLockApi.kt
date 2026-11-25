package gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire

import gog.my_project.data_base_session.api.interfaces.api.ISessionCommandApi
import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import kotlin.time.Duration

interface IAcquireSessionAdvisoryLockApi :
    ISessionCommandApi<IAcquireSessionAdvisoryLockAst, AcquireSessionLockResult> {

    fun resource(namespace: String, name: String): IAcquireSessionAdvisoryLockApi

    fun waitTimeout(duration: Duration): IAcquireSessionAdvisoryLockApi

    fun noWait(): IAcquireSessionAdvisoryLockApi

    override fun dispatch(executor: ISessionCommandExecutor): AcquireSessionLockResult =
        executor.acquire(ast)
}
