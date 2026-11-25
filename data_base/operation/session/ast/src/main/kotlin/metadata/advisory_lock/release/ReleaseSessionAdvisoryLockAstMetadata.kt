package gog.my_project.data_base_session.ast.metadata.advisory_lock.release

import gog.my_project.data_base_session.ast.metadata.interfaces.ISessionAstMetadata
import gog.my_project.data_base_session.ast.value.LockResource

class ReleaseSessionAdvisoryLockAstMetadata : ISessionAstMetadata {
    var resource: LockResource? = null
}
