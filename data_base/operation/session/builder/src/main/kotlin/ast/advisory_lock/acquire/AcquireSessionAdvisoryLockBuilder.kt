package gog.my_project.data_base_session.builder.ast.advisory_lock.acquire

import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import kotlin.time.Duration

class AcquireSessionAdvisoryLockBuilder(
    override var ast: IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst(),
) : IAcquireSessionAdvisoryLockApi {

    override fun resource(namespace: String, name: String): IAcquireSessionAdvisoryLockApi {
        ast.metadata.resource = LockResource(namespace, name)
        return this
    }

    override fun waitTimeout(duration: Duration): IAcquireSessionAdvisoryLockApi {
        ast.metadata.waitPolicy = LockWaitPolicy.Timeout.of(duration)
        return this
    }

    override fun noWait(): IAcquireSessionAdvisoryLockApi {
        ast.metadata.waitPolicy = LockWaitPolicy.NoWait
        return this
    }
}
