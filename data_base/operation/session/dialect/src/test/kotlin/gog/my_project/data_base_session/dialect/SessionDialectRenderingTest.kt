package gog.my_project.data_base_session.dialect

import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.dialect.interfaces.ISessionAstRenderer
import gog.my_project.data_base_session.dialect.interfaces.ISessionRenderContext
import gog.my_project.data_base_session.dialect.interfaces.ISessionRendererRegistry
import gog.my_project.data_base_session.dialect.manager.BaseSessionDialect
import gog.my_project.data_base_session.dialect.manager.SessionRenderState
import gog.my_project.data_base_session.dialect.manager.SessionRendererRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun parameter(name: String, value: Any?, sqlType: Int) =
    SessionCommandParameter(name, value, sqlType)

class SessionDialectRenderingTest {

    @Test
    fun `registry registers retrieves and resolves a renderer by AST interface`() {
        val registry = SessionRendererRegistry()
        val generalRenderer = FixedRenderer<ISessionCommandAst>("general")
        val acquireRenderer = FixedRenderer<IAcquireSessionAdvisoryLockAst>("acquire")
        registry.register(ISessionCommandAst::class, generalRenderer)
        registry.register(IAcquireSessionAdvisoryLockAst::class, acquireRenderer)

        assertSame(acquireRenderer, registry.get(IAcquireSessionAdvisoryLockAst::class))
        assertSame(acquireRenderer, registry.resolve(validAcquireAst()))
    }

    @Test
    fun `duplicate renderer registration is rejected`() {
        val registry = SessionRendererRegistry()
        registry.register(
            IAcquireSessionAdvisoryLockAst::class,
            FixedRenderer<IAcquireSessionAdvisoryLockAst>("first"),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            registry.register(
                IAcquireSessionAdvisoryLockAst::class,
                FixedRenderer<IAcquireSessionAdvisoryLockAst>("second"),
            )
        }

        assertTrue(failure.message.orEmpty().contains("already registered"))
    }

    @Test
    fun `more specific AST interface wins over its command parent`() {
        val dialect = TestDialect {
            register(ISessionCommandAst::class, FixedRenderer<ISessionCommandAst>("general"))
            register(
                IAcquireSessionAdvisoryLockAst::class,
                FixedRenderer<IAcquireSessionAdvisoryLockAst>("specialized"),
            )
        }

        val rendered = dialect.render(validAcquireAst())

        assertEquals("specialized", rendered.sql)
        assertEquals(DatabaseDialect.MYSQL, dialect.dialect)
    }

    @Test
    fun `multiple unrelated matching AST interfaces fail as ambiguous`() {
        val registry = SessionRendererRegistry().apply {
            register(FirstCommandAst::class, FixedRenderer<FirstCommandAst>("first"))
            register(SecondCommandAst::class, FixedRenderer<SecondCommandAst>("second"))
        }

        val failure = assertFailsWith<SessionRenderException.AmbiguousRenderer> {
            registry.resolve(AmbiguousCommandAst())
        }

        assertTrue(failure.message.orEmpty().contains("Multiple Session renderers match"))
    }

    @Test
    fun `parameter placeholders and occurrences preserve emission order`() {
        val dialect = TestDialect {
            register(IAcquireSessionAdvisoryLockAst::class, OrderedParameterRenderer())
        }

        val rendered = dialect.render(validAcquireAst())

        assertEquals("? + ?", rendered.sql)
        assertEquals(listOf("first", "second"), rendered.parameters.map { it.name })
        assertEquals(listOf("a", 2), rendered.parameters.map { it.value })
        assertEquals(listOf(12, 4), rendered.parameters.map { it.sqlType })
    }

    @Test
    fun `nested dispatch creates child context while sharing occurrence state`() {
        val childAst = validReleaseAst()
        val dialect = TestDialect {
            register(IAcquireSessionAdvisoryLockAst::class, NestedAcquireRenderer(childAst))
            register(IReleaseSessionAdvisoryLockAst::class, ReleaseParameterRenderer())
        }

        val rendered = dialect.render(validAcquireAst())

        assertEquals("? + (?) + ?", rendered.sql)
        assertEquals(listOf("parent-before", "release", "parent-after"), rendered.parameters.map { it.name })
    }

    @Test
    fun `snapshot is independent from later state mutations`() {
        val state = SessionRenderState()
        val first = parameter("first", "one", 12)
        val second = parameter("second", "two", 12)

        state.emit(first)
        val snapshot = state.snapshot()
        state.emit(second)

        assertEquals(listOf(first), snapshot)
        assertEquals(listOf(first, second), state.snapshot())
    }

    @Test
    fun `checkpoint rollback removes only occurrences emitted after checkpoint`() {
        val state = SessionRenderState()
        val retained = parameter("retained", 1, 4)
        state.emit(retained)
        val checkpoint = state.checkpoint()
        state.emit(parameter("discarded", 2, 4))

        state.rollbackTo(checkpoint)

        assertEquals(listOf(retained), state.snapshot())
    }

    @Test
    fun `each top level command receives independent state and output snapshot`() {
        val dialect = TestDialect {
            register(IAcquireSessionAdvisoryLockAst::class, OrderedParameterRenderer())
            register(IReleaseSessionAdvisoryLockAst::class, ReleaseParameterRenderer())
        }

        val acquire = dialect.render(validAcquireAst())
        val release = dialect.render(validReleaseAst())

        assertEquals(SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK, acquire.kind)
        assertEquals(SessionCommandKind.RELEASE_SESSION_ADVISORY_LOCK, release.kind)
        assertEquals(listOf("first", "second"), acquire.parameters.map { it.name })
        assertEquals(listOf("release"), release.parameters.map { it.name })
        assertEquals(listOf("first", "second"), acquire.parameters.map { it.name })
    }

    @Test
    fun `missing renderer is reported with the AST type`() {
        val dialect = TestDialect()

        val failure = assertFailsWith<SessionRenderException.RendererNotFound> {
            dialect.render(validAcquireAst())
        }

        assertTrue(failure.message.orEmpty().contains("AcquireSessionAdvisoryLockAst"))
    }

    @Test
    fun `missing required AST metadata is reported before dispatch`() {
        val dialect = TestDialect {
            register(
                IAcquireSessionAdvisoryLockAst::class,
                FixedRenderer<IAcquireSessionAdvisoryLockAst>("unused"),
            )
        }

        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            dialect.render(AcquireSessionAdvisoryLockAst())
        }

        assertTrue(failure.message.orEmpty().contains("resource is missing"))
    }

    @Test
    fun `acquire AST without wait policy is rejected`() {
        val ast = AcquireSessionAdvisoryLockAst().apply {
            metadata.resource = LockResource("application", "migration")
        }

        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            TestDialect().render(ast)
        }

        assertTrue(failure.message.orEmpty().contains("wait policy is missing"))
    }

    @Test
    fun `release AST without resource is rejected`() {
        val failure = assertFailsWith<SessionRenderException.InvalidAst> {
            TestDialect().render(ReleaseSessionAdvisoryLockAst())
        }

        assertTrue(failure.message.orEmpty().contains("Release lock resource is missing"))
    }

    @Test
    fun `null renderer result rolls back its occurrences and becomes a render error`() {
        val registry = SessionRendererRegistry().apply {
            register(IAcquireSessionAdvisoryLockAst::class, NullAfterEmissionRenderer())
        }
        val dialect = TestDialect()
        val state = SessionRenderState()

        assertNull(registry.render(validAcquireAst(), dialect, state))
        assertEquals(emptyList(), state.snapshot())

        val rejectingDialect = TestDialect {
            register(IAcquireSessionAdvisoryLockAst::class, NullAfterEmissionRenderer())
        }
        assertFailsWith<SessionRenderException.RendererReturnedNull> {
            rejectingDialect.render(validAcquireAst())
        }
    }

    @Test
    fun `renderer exception preserves cause and rolls back occurrences`() {
        val registry = SessionRendererRegistry().apply {
            register(IAcquireSessionAdvisoryLockAst::class, ThrowAfterEmissionRenderer())
        }
        val state = SessionRenderState()

        val failure = assertFailsWith<SessionRenderException.RendererFailed> {
            registry.render(validAcquireAst(), TestDialect(), state)
        }

        assertIs<IllegalStateException>(failure.cause)
        assertEquals(emptyList(), state.snapshot())
    }

    private fun validAcquireAst() = AcquireSessionAdvisoryLockAst().apply {
        metadata.resource = LockResource("application", "migration")
        metadata.waitPolicy = LockWaitPolicy.NoWait
    }

    private fun validReleaseAst() = ReleaseSessionAdvisoryLockAst().apply {
        metadata.resource = LockResource("application", "migration")
    }

    private class TestDialect(
        registerRenders: ISessionRendererRegistry.() -> Unit = {},
    ) : BaseSessionDialect(DatabaseDialect.MYSQL, registerRenders)

    private class FixedRenderer<A : ISessionCommandAst>(
        private val sql: String?,
    ) : ISessionAstRenderer<A> {
        override fun render(ast: A, context: ISessionRenderContext): String? = sql
    }

    private class OrderedParameterRenderer : ISessionAstRenderer<IAcquireSessionAdvisoryLockAst> {
        override fun render(
            ast: IAcquireSessionAdvisoryLockAst,
            context: ISessionRenderContext,
        ): String {
            val first = context.emitParameter(parameter("first", "a", 12))
            val second = context.emitParameter(parameter("second", 2, 4))
            return "$first + $second"
        }
    }

    private class NestedAcquireRenderer(
        private val childAst: IReleaseSessionAdvisoryLockAst,
    ) : ISessionAstRenderer<IAcquireSessionAdvisoryLockAst> {
        override fun render(
            ast: IAcquireSessionAdvisoryLockAst,
            context: ISessionRenderContext,
        ): String {
            val before = context.emitParameter(parameter("parent-before", 1, 4))
            val child = context.render(childAst)
            val after = context.emitParameter(parameter("parent-after", 3, 4))
            return "$before + ($child) + $after"
        }
    }

    private class ReleaseParameterRenderer : ISessionAstRenderer<IReleaseSessionAdvisoryLockAst> {
        override fun render(
            ast: IReleaseSessionAdvisoryLockAst,
            context: ISessionRenderContext,
        ): String {
            val placeholder = context.emitParameter(parameter("release", "lock", 12))
            return placeholder
        }
    }

    private class NullAfterEmissionRenderer : ISessionAstRenderer<IAcquireSessionAdvisoryLockAst> {
        override fun render(
            ast: IAcquireSessionAdvisoryLockAst,
            context: ISessionRenderContext,
        ): String? {
            context.emitParameter(parameter("discarded", "value", 12))
            return null
        }
    }

    private class ThrowAfterEmissionRenderer : ISessionAstRenderer<IAcquireSessionAdvisoryLockAst> {
        override fun render(
            ast: IAcquireSessionAdvisoryLockAst,
            context: ISessionRenderContext,
        ): String {
            context.emitParameter(parameter("discarded", "value", 12))
            throw IllegalStateException("renderer test failure")
        }
    }

    private interface FirstCommandAst : ISessionCommandAst

    private interface SecondCommandAst : ISessionCommandAst

    private data class AmbiguousCommandAst(
        override val kind: SessionCommandKind = SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK,
    ) : FirstCommandAst, SecondCommandAst
}
