package gog.my_project.data_base_session.renderer.nodes.advisory_lock.acquire

import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.ast.value.SessionAdvisoryLockNameGenerator
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionAstRenderer
import gog.my_project.data_base_session.dialect.interfaces.ISessionRenderContext
import java.sql.Types

internal class AcquireSessionAdvisoryLockRenderer : ISessionAstRenderer<IAcquireSessionAdvisoryLockAst> {

    override fun render(
        ast: IAcquireSessionAdvisoryLockAst,
        context: ISessionRenderContext,
    ): String {
        val metadata = ast.metadata
        val resource = metadata.resource
            ?: throw SessionRenderException.InvalidAst(
                "Acquire Session Advisory Lock metadata.resource is missing.",
            )
        val waitPolicy = metadata.waitPolicy
            ?: throw SessionRenderException.InvalidAst(
                "Acquire Session Advisory Lock metadata.waitPolicy is missing.",
            )

        val lockName = SessionAdvisoryLockNameGenerator.generate(resource)
        val timeoutSeconds = when (waitPolicy) {
            LockWaitPolicy.NoWait -> 0
            is LockWaitPolicy.Timeout -> waitPolicy.seconds
        }

        val lockNamePlaceholder = context.emitParameter(
            SessionCommandParameter(
                name = "lockName",
                value = lockName,
                sqlType = Types.VARCHAR,
            ),
        )
        val timeoutPlaceholder = context.emitParameter(
            SessionCommandParameter(
                name = "timeoutSeconds",
                value = timeoutSeconds,
                sqlType = Types.INTEGER,
            ),
        )

        return "SELECT GET_LOCK($lockNamePlaceholder, $timeoutPlaceholder) AS acquired"
    }
}
