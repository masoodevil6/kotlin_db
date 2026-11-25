package gog.my_project.data_base_session.builder

import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.acquire.IAcquireSessionAdvisoryLockApi
import gog.my_project.data_base_session.api.interfaces.api.advisory_lock.release.IReleaseSessionAdvisoryLockApi
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import gog.my_project.data_base_session.builder.ast.advisory_lock.acquire.AcquireSessionAdvisoryLockBuilder
import gog.my_project.data_base_session.builder.ast.advisory_lock.release.ReleaseSessionAdvisoryLockBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SessionAdvisoryLockBuilderTest {

    @Test
    fun `acquire builder creates typed AST with empty metadata by default`() {
        val builder = AcquireSessionAdvisoryLockBuilder()

        assertIs<AcquireSessionAdvisoryLockAst>(builder.ast)
        assertEquals(null, builder.ast.metadata.resource)
        assertEquals(null, builder.ast.metadata.waitPolicy)
    }

    @Test
    fun `acquire builder mutates injected AST and preserves other metadata fields`() {
        val injectedAst = AcquireSessionAdvisoryLockAst().apply {
            metadata.waitPolicy = LockWaitPolicy.NoWait
        }
        val injectedMetadata = injectedAst.metadata
        val builder = AcquireSessionAdvisoryLockBuilder(injectedAst)

        val returned = builder.resource("application", "migration")

        assertSame(builder, returned)
        assertSame(injectedAst, builder.ast)
        assertSame(injectedMetadata, builder.ast.metadata)
        assertEquals(LockResource("application", "migration"), builder.ast.metadata.resource)
        assertSame(LockWaitPolicy.NoWait, builder.ast.metadata.waitPolicy)

        builder.waitTimeout(30.seconds)
        assertEquals(LockResource("application", "migration"), builder.ast.metadata.resource)
        assertEquals(30, (builder.ast.metadata.waitPolicy as LockWaitPolicy.Timeout).seconds)
    }

    @Test
    fun `acquire builder replaces resource and wait policy on repeated calls`() {
        val builder = AcquireSessionAdvisoryLockBuilder()

        builder.resource("first", "one")
            .resource("second", "two")
            .waitTimeout(30.seconds)
            .noWait()

        assertEquals(LockResource("second", "two"), builder.ast.metadata.resource)
        assertSame(LockWaitPolicy.NoWait, builder.ast.metadata.waitPolicy)
    }

    @Test
    fun `release builder creates typed AST with empty metadata by default`() {
        val builder = ReleaseSessionAdvisoryLockBuilder()

        assertIs<ReleaseSessionAdvisoryLockAst>(builder.ast)
        assertEquals(null, builder.ast.metadata.resource)
    }

    @Test
    fun `release builder mutates injected AST`() {
        val injectedAst = ReleaseSessionAdvisoryLockAst()
        val injectedMetadata = injectedAst.metadata
        val builder = ReleaseSessionAdvisoryLockBuilder(injectedAst)

        val returned = builder.resource("application", "migration")

        assertSame(builder, returned)
        assertSame(injectedAst, builder.ast)
        assertSame(injectedMetadata, builder.ast.metadata)
        assertEquals(LockResource("application", "migration"), builder.ast.metadata.resource)
    }

    @Test
    fun `factories return typed valid command APIs`() {
        val acquire: IAcquireSessionAdvisoryLockApi = acquireSessionAdvisoryLock {
            resource("application", "migration")
            waitTimeout(30.seconds)
        }
        val release: IReleaseSessionAdvisoryLockApi = releaseSessionAdvisoryLock {
            resource("application", "migration")
        }

        assertNotNull(acquire.ast.metadata.resource)
        assertNotNull(acquire.ast.metadata.waitPolicy)
        assertNotNull(release.ast.metadata.resource)
    }

    @Test
    fun `builders expose only their matching API and AST types`() {
        val acquireApi: IAcquireSessionAdvisoryLockApi = AcquireSessionAdvisoryLockBuilder()
        val acquireAst: IAcquireSessionAdvisoryLockAst = acquireApi.ast
        val releaseApi: IReleaseSessionAdvisoryLockApi = ReleaseSessionAdvisoryLockBuilder()
        val releaseAst: IReleaseSessionAdvisoryLockAst = releaseApi.ast

        assertIs<IAcquireSessionAdvisoryLockAst>(acquireAst)
        assertIs<IReleaseSessionAdvisoryLockAst>(releaseAst)
    }

    @Test
    fun `acquire factory rejects missing resource and wait policy with specific messages`() {
        val missingResource = assertFailsWith<IllegalArgumentException> {
            acquireSessionAdvisoryLock { noWait() }
        }
        val missingWaitPolicy = assertFailsWith<IllegalArgumentException> {
            acquireSessionAdvisoryLock { resource("application", "migration") }
        }

        assertTrue(missingResource.message.orEmpty().contains("requires a resource"))
        assertTrue(missingWaitPolicy.message.orEmpty().contains("requires a wait policy"))
    }

    @Test
    fun `release factory rejects missing resource with specific message`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            releaseSessionAdvisoryLock { }
        }

        assertTrue(exception.message.orEmpty().contains("requires a resource"))
    }

    @Test
    fun `acquire and release APIs expose only their command methods`() {
        val acquireMethodNames = IAcquireSessionAdvisoryLockApi::class.java.methods
            .map { it.name }
            .toSet()
        val releaseMethodNames = IReleaseSessionAdvisoryLockApi::class.java.methods
            .map { it.name }
            .toSet()

        assertTrue(acquireMethodNames.containsAll(setOf("resource", "noWait")))
        assertTrue(acquireMethodNames.any { it.contains("waitTimeout") })
        assertTrue("noWait" !in releaseMethodNames)
        assertTrue(releaseMethodNames.contains("resource"))
        assertTrue(releaseMethodNames.none { it.contains("waitTimeout") })
    }
}
