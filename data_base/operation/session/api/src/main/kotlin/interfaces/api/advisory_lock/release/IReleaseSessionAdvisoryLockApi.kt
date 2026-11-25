package gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release

import gog.my_project.data_base_session.api.interfaces.api.ISessionCommandApi
import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult

interface IReleaseSessionAdvisoryLockApi :
    ISessionCommandApi<IReleaseSessionAdvisoryLockAst, ReleaseSessionLockResult> {

    fun resource(namespace: String, name: String): IReleaseSessionAdvisoryLockApi

    override fun dispatch(executor: ISessionCommandExecutor): ReleaseSessionLockResult =
        executor.release(ast)
}
