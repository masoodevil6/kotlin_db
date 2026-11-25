package gog.my_project.data_base_session.api

import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.ISessionApi
import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.api.result.SessionExecutionResult
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SessionApiContractTest {
    @Test
    fun `acquire API exposes acquire AST and fluent acquire operations`() {
        val ast: IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst()
        val api: IAcquireSessionAdvisoryLockApi = AcquireApiStub(ast)

        val exposedAst: IAcquireSessionAdvisoryLockAst = api.ast
        val fluentApi: IAcquireSessionAdvisoryLockApi = api
            .resource("application", "migration")
            .waitTimeout(30.seconds)
            .noWait()

        assertSame(ast, exposedAst)
        assertSame(api, fluentApi)
    }

    @Test
    fun `release API exposes release AST and resource only`() {
        val ast: IReleaseSessionAdvisoryLockAst = ReleaseSessionAdvisoryLockAst()
        val api: IReleaseSessionAdvisoryLockApi = ReleaseApiStub(ast)

        val exposedAst: IReleaseSessionAdvisoryLockAst = api.ast
        val fluentApi: IReleaseSessionAdvisoryLockApi = api.resource("application", "migration")

        assertSame(ast, exposedAst)
        assertSame(api, fluentApi)
    }

    @Test
    fun `acquire dispatch forwards the exact AST and preserves the typed result`() {
        val ast = AcquireSessionAdvisoryLockAst()
        val api: IAcquireSessionAdvisoryLockApi = AcquireApiStub(ast)
        val executor = RecordingSessionCommandExecutor()

        val result: AcquireSessionLockResult = api.dispatch(executor)

        assertSame(ast, executor.acquiredAst)
        assertSame(AcquireSessionLockResult.Acquired, result)
        assertEquals(1, executor.acquireCalls)
        assertEquals(0, executor.releaseCalls)
    }

    @Test
    fun `release dispatch forwards the exact AST and preserves the typed result`() {
        val ast = ReleaseSessionAdvisoryLockAst()
        val api: IReleaseSessionAdvisoryLockApi = ReleaseApiStub(ast)
        val executor = RecordingSessionCommandExecutor()

        val result: ReleaseSessionLockResult = api.dispatch(executor)

        assertSame(ast, executor.releasedAst)
        assertSame(ReleaseSessionLockResult.Released, result)
        assertEquals(0, executor.acquireCalls)
        assertEquals(1, executor.releaseCalls)
    }

    @Test
    fun `existing single generic session API contract remains implementable`() {
        val ast: IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst()
        val api: ISessionApi<IAcquireSessionAdvisoryLockAst> = LegacySessionApiStub(ast)

        assertSame(ast, api.ast)
    }

    @Test
    fun `acquire and release results retain distinct semantic types`() {
        val acquireResults: List<AcquireSessionLockResult> = listOf(
            AcquireSessionLockResult.Acquired,
            AcquireSessionLockResult.TimedOut,
        )
        val releaseResults: List<ReleaseSessionLockResult> = listOf(
            ReleaseSessionLockResult.Released,
            ReleaseSessionLockResult.NotOwned,
        )

        val commonResults: List<SessionExecutionResult> = acquireResults + releaseResults

        assertIs<AcquireSessionLockResult>(commonResults[0])
        assertIs<AcquireSessionLockResult>(commonResults[1])
        assertIs<ReleaseSessionLockResult>(commonResults[2])
        assertIs<ReleaseSessionLockResult>(commonResults[3])
    }

    private class AcquireApiStub(
        override var ast: IAcquireSessionAdvisoryLockAst,
    ) : IAcquireSessionAdvisoryLockApi {
        override fun resource(namespace: String, name: String): IAcquireSessionAdvisoryLockApi = this

        override fun waitTimeout(duration: Duration): IAcquireSessionAdvisoryLockApi = this

        override fun noWait(): IAcquireSessionAdvisoryLockApi = this
    }

    private class ReleaseApiStub(
        override var ast: IReleaseSessionAdvisoryLockAst,
    ) : IReleaseSessionAdvisoryLockApi {
        override fun resource(namespace: String, name: String): IReleaseSessionAdvisoryLockApi = this
    }

    private class LegacySessionApiStub(
        override var ast: IAcquireSessionAdvisoryLockAst,
    ) : ISessionApi<IAcquireSessionAdvisoryLockAst>

    private class RecordingSessionCommandExecutor : ISessionCommandExecutor {
        var acquiredAst: IAcquireSessionAdvisoryLockAst? = null
        var releasedAst: IReleaseSessionAdvisoryLockAst? = null
        var acquireCalls: Int = 0
        var releaseCalls: Int = 0

        override fun acquire(ast: IAcquireSessionAdvisoryLockAst): AcquireSessionLockResult {
            acquiredAst = ast
            acquireCalls += 1
            return AcquireSessionLockResult.Acquired
        }

        override fun release(ast: IReleaseSessionAdvisoryLockAst): ReleaseSessionLockResult {
            releasedAst = ast
            releaseCalls += 1
            return ReleaseSessionLockResult.Released
        }
    }
}
