package gog.my_project.data_base_session.ast.schema.advisory_lock.release

import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata

class ReleaseSessionAdvisoryLockAst(
    override var metadata: ReleaseSessionAdvisoryLockAstMetadata = ReleaseSessionAdvisoryLockAstMetadata(),
) : IReleaseSessionAdvisoryLockAst {
    override val kind: SessionCommandKind = SessionCommandKind.RELEASE_SESSION_ADVISORY_LOCK
}
