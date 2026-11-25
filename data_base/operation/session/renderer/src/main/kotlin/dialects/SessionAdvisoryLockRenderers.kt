package gog.my_project.data_base_session.renderer.dialects

import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry
import gog.my_project.data_base_session.renderer.nodes.advisory_lock.acquire.AcquireSessionAdvisoryLockRenderer
import gog.my_project.data_base_session.renderer.nodes.advisory_lock.release.ReleaseSessionAdvisoryLockRenderer

internal fun ISessionRendererRegistry.registerMySqlCompatibleAdvisoryLockRenderers() {
    register(IAcquireSessionAdvisoryLockAst::class, AcquireSessionAdvisoryLockRenderer())
    register(IReleaseSessionAdvisoryLockAst::class, ReleaseSessionAdvisoryLockRenderer())
}
