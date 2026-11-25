package gog.my_project.data_base_session.ast.schema.advisory_lock.acquire

import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata

class AcquireSessionAdvisoryLockAst(
    override var metadata: AcquireSessionAdvisoryLockAstMetadata = AcquireSessionAdvisoryLockAstMetadata(),
) : IAcquireSessionAdvisoryLockAst {
    override val kind: SessionCommandKind = SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK
}
