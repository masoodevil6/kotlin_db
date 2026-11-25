package gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire

import gog.my_project.data_base_session.ast.metadata.interfaces.ISessionAstMetadata
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy

class AcquireSessionAdvisoryLockAstMetadata : ISessionAstMetadata {
    var resource: LockResource? = null
    var waitPolicy: LockWaitPolicy? = null
}
