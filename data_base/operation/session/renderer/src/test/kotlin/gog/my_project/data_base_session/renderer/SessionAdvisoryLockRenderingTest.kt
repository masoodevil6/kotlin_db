package gog.my_project.data_base_session.renderer

import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.ast.value.SessionAdvisoryLockNameGenerator
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionDialect
import gog.my_project.data_base_session.dialect.interfaces.ISessionRenderContext
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry
import gog.my_project.data_base_session.dialect.manager.SessionRenderState
import gog.my_project.data_base_session.renderer.dialects.MariaDbSessionDialect
import gog.my_project.data_base_session.renderer.dialects.MySqlSessionDialect
import gog.my_project.data_base_session.renderer.nodes.advisory_lock.acquire.AcquireSessionAdvisoryLockRenderer
import gog.my_project.data_base_session.renderer.nodes.advisory_lock.release.ReleaseSessionAdvisoryLockRenderer
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class SessionAdvisoryLockRenderingTest {

    private val resource = LockResource(namespace = "migration", name = "database-main")
    private val goldenLockName =
        "kdb1_22f00cc29957a0c2cfb8e5b0106ce5b4dd546ef0173cc52970e19a5fa6"

    @Test
    fun `MySQL renders acquire SQL and parameters in placeholder order`() {
        val rendered = MySqlSessionDialect().render(acquire(LockWaitPolicy.Timeout.of(60.seconds)))

        assertEquals(SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK, rendered.kind)
        assertEquals("SELECT GET_LOCK(?, ?) AS acquired", rendered.sql)
        assertEquals(listOf("lockName", "timeoutSeconds"), rendered.parameters.map { it.name })
        assertEquals(listOf(goldenLockName, 60), rendered.parameters.map { it.value })
        assertEquals(listOf(Types.VARCHAR, Types.INTEGER), rendered.parameters.map { it.sqlType })
    }

    @Test
    fun `MySQL renders release SQL with the generated lock name`() {
        val rendered = MySqlSessionDialect().render(release())

        assertEquals(SessionCommandKind.RELEASE_SESSION_ADVISORY_LOCK, rendered.kind)
        assertEquals("SELECT RELEASE_LOCK(?) AS released", rendered.sql)
        assertEquals(listOf("lockName"), rendered.parameters.map { it.name })
        assertEquals(listOf(goldenLockName), rendered.parameters.map { it.value })
        assertEquals(listOf(Types.VARCHAR), rendered.parameters.map { it.sqlType })
    }

    @Test
    fun `MariaDB renders acquire SQL and ordered parameters`() {
        val rendered = MariaDbSessionDialect().render(acquire(LockWaitPolicy.Timeout.of(30.seconds)))

        assertEquals("SELECT GET_LOCK(?, ?) AS acquired", rendered.sql)
        assertEquals(listOf(goldenLockName, 30), rendered.parameters.map { it.value })
        assertEquals(listOf(Types.VARCHAR, Types.INTEGER), rendered.parameters.map { it.sqlType })
    }

    @Test
    fun `MariaDB renders release SQL and lock name`() {
        val rendered = MariaDbSessionDialect().render(release())

        assertEquals("SELECT RELEASE_LOCK(?) AS released", rendered.sql)
        assertEquals(listOf(goldenLockName), rendered.parameters.map { it.value })
        assertEquals(listOf(Types.VARCHAR), rendered.parameters.map { it.sqlType })
    }

    @Test
    fun `NoWait emits zero seconds as an integer parameter`() {
        val rendered = MySqlSessionDialect().render(acquire(LockWaitPolicy.NoWait))

        assertEquals(0, rendered.parameters[1].value)
        assertEquals(Types.INTEGER, rendered.parameters[1].sqlType)
    }

    @Test
    fun `Timeout emits its validated whole seconds`() {
        val rendered = MariaDbSessionDialect().render(acquire(LockWaitPolicy.Timeout.of(75.seconds)))

        assertEquals(75, rendered.parameters[1].value)
        assertEquals(Types.INTEGER, rendered.parameters[1].sqlType)
    }

    @Test
    fun `generated lock name matches the established golden vector`() {
        assertEquals(goldenLockName, SessionAdvisoryLockNameGenerator.generate(resource))
    }

    @Test
    fun `acquire and release use the same lock name algorithm`() {
        val acquireName = MySqlSessionDialect()
            .render(acquire(LockWaitPolicy.NoWait))
            .parameters.first().value
        val releaseName = MariaDbSessionDialect()
            .render(release())
            .parameters.single().value

        assertEquals(acquireName, releaseName)
    }

    @Test
    fun `rendered parameter snapshot remains unchanged by later commands`() {
        val dialect = MySqlSessionDialect()
        val first = dialect.render(acquire(LockWaitPolicy.Timeout.of(15.seconds)))
        val firstParameters = first.parameters.toList()

        dialect.render(release())

        assertEquals(firstParameters, first.parameters)
        assertEquals(listOf(goldenLockName, 15), first.parameters.map { it.value })
    }

    @Test
    fun `acquire rejects metadata without a resource`() {
        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            AcquireSessionAdvisoryLockRenderer().render(AcquireSessionAdvisoryLockAst().apply {
                metadata.waitPolicy = LockWaitPolicy.NoWait
            }, TestRenderContext())
        }

        assertIs<SessionRenderException.InvalidAst>(failure)
        kotlin.test.assertTrue(failure.message.orEmpty().contains("resource"))
    }

    @Test
    fun `acquire rejects metadata without a wait policy`() {
        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            AcquireSessionAdvisoryLockRenderer().render(AcquireSessionAdvisoryLockAst().apply {
                metadata.resource = resource
            }, TestRenderContext())
        }

        kotlin.test.assertTrue(failure.message.orEmpty().contains("waitPolicy"))
    }

    @Test
    fun `release rejects invalid metadata without a resource`() {
        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            ReleaseSessionAdvisoryLockRenderer().render(ReleaseSessionAdvisoryLockAst(), TestRenderContext())
        }

        kotlin.test.assertTrue(failure.message.orEmpty().contains("resource"))
    }

    @Test
    fun `both dialects register renderers for acquire and release`() {
        val mysql = MySqlSessionDialect()
        val mariaDb = MariaDbSessionDialect()

        assertEquals("SELECT GET_LOCK(?, ?) AS acquired", mysql.render(acquire(LockWaitPolicy.NoWait)).sql)
        assertEquals("SELECT RELEASE_LOCK(?) AS released", mysql.render(release()).sql)
        assertEquals("SELECT GET_LOCK(?, ?) AS acquired", mariaDb.render(acquire(LockWaitPolicy.NoWait)).sql)
        assertEquals("SELECT RELEASE_LOCK(?) AS released", mariaDb.render(release()).sql)
    }

    private fun acquire(waitPolicy: LockWaitPolicy) = AcquireSessionAdvisoryLockAst().apply {
        metadata.resource = resource
        metadata.waitPolicy = waitPolicy
    }

    private fun release() = ReleaseSessionAdvisoryLockAst().apply {
        metadata.resource = resource
    }

    private class TestRenderContext : ISessionRenderContext {
        override val dialect: ISessionDialect = MySqlSessionDialect()
        override val registry: ISessionRendererRegistry = dialect.registry
        private val state = SessionRenderState()

        override fun emitParameter(parameter: SessionCommandParameter): String {
            state.emit(parameter)
            return "?"
        }

        override fun checkpoint(): SessionRenderState.Checkpoint = state.checkpoint()

        override fun rollbackTo(checkpoint: SessionRenderState.Checkpoint) {
            state.rollbackTo(checkpoint)
        }

        override fun render(ast: gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst): String =
            registry.render(ast, dialect, state)
                ?: throw SessionRenderException.RendererReturnedNull(ast::class)
    }
}
