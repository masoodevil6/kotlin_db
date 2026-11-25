package gog.my_project.data_base_session.api.interfaces.executor

import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult

interface ISessionCommandExecutor {
    fun acquire(
        ast: IAcquireSessionAdvisoryLockAst,
    ): AcquireSessionLockResult

    fun release(
        ast: IReleaseSessionAdvisoryLockAst,
    ): ReleaseSessionLockResult
}
