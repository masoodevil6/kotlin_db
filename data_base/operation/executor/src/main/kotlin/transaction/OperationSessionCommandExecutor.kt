package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.sql.Connection

internal class OperationSessionCommandExecutor(
    private val connection: Connection,
    private val dialect: DatabaseDialect,
) : ISessionCommandExecutor {
    private val sessionExecutor = SessionExecutor()

    override fun acquire(
        ast: IAcquireSessionAdvisoryLockAst,
    ): AcquireSessionLockResult = sessionExecutor.acquire(ast, connection, dialect)

    override fun release(
        ast: IReleaseSessionAdvisoryLockAst,
    ): ReleaseSessionLockResult = sessionExecutor.release(ast, connection, dialect)
}
