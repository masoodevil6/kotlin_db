package gog.my_project.data_base_session.ast.interfaces.advisory_lock.release

import gog.my_project.data_base_session.ast.interfaces.ISessionAst
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata

interface IReleaseSessionAdvisoryLockAst :
    ISessionAst<ReleaseSessionAdvisoryLockAstMetadata>,
    ISessionCommandAst
