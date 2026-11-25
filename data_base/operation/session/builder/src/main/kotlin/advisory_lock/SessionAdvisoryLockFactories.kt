package gog.my_project.data_base_session.builder.advisory_lock

import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.builder.ast.advisory_lock.acquire.AcquireSessionAdvisoryLockBuilder
import gog.my_project.data_base_session.builder.ast.advisory_lock.release.ReleaseSessionAdvisoryLockBuilder

fun acquireSessionAdvisoryLock(
    block: IAcquireSessionAdvisoryLockApi.() -> Unit,
): IAcquireSessionAdvisoryLockApi {
    val builder = AcquireSessionAdvisoryLockBuilder().apply(block)

    requireNotNull(builder.ast.metadata.resource) {
        "Acquire session advisory lock requires a resource."
    }
    requireNotNull(builder.ast.metadata.waitPolicy) {
        "Acquire session advisory lock requires a wait policy."
    }

    return builder
}

fun releaseSessionAdvisoryLock(
    block: IReleaseSessionAdvisoryLockApi.() -> Unit,
): IReleaseSessionAdvisoryLockApi {
    val builder = ReleaseSessionAdvisoryLockBuilder().apply(block)

    requireNotNull(builder.ast.metadata.resource) {
        "Release session advisory lock requires a resource."
    }

    return builder
}
