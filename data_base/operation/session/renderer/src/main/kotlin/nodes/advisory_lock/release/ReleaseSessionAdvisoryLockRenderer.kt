package gog.my_project.data_base_session.renderer.nodes.advisory_lock.release

import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.SessionAdvisoryLockNameGenerator
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionAstRenderer
import gog.my_project.data_base_session.dialect.interfaces.ISessionRenderContext
import java.sql.Types

internal class ReleaseSessionAdvisoryLockRenderer : ISessionAstRenderer<IReleaseSessionAdvisoryLockAst> {

    override fun render(
        ast: IReleaseSessionAdvisoryLockAst,
        context: ISessionRenderContext,
    ): String {
        val resource = ast.metadata.resource
            ?: throw SessionRenderException.InvalidAst(
                "Release Session Advisory Lock metadata.resource is missing.",
            )
        val lockName = SessionAdvisoryLockNameGenerator.generate(resource)
        val lockNamePlaceholder = context.emitParameter(
            SessionCommandParameter(
                name = "lockName",
                value = lockName,
                sqlType = Types.VARCHAR,
            ),
        )

        return "SELECT RELEASE_LOCK($lockNamePlaceholder) AS released"
    }
}
