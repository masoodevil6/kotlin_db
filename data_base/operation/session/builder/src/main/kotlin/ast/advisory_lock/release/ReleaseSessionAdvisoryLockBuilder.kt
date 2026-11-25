package gog.my_project.data_base_session.builder.ast.advisory_lock.release

import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource

class ReleaseSessionAdvisoryLockBuilder(
    override var ast: IReleaseSessionAdvisoryLockAst = ReleaseSessionAdvisoryLockAst(),
) : IReleaseSessionAdvisoryLockApi {

    override fun resource(namespace: String, name: String): IReleaseSessionAdvisoryLockApi {
        ast.metadata.resource = LockResource(namespace, name)
        return this
    }
}
