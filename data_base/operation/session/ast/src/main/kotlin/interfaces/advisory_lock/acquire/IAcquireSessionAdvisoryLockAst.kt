package gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire

import gog.my_project.data_base_session.ast.interfaces.ISessionAst
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata

interface IAcquireSessionAdvisoryLockAst :
    ISessionAst<AcquireSessionAdvisoryLockAstMetadata>,
    ISessionCommandAst
