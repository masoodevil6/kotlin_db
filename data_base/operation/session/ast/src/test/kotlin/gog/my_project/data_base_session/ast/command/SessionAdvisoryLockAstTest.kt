package gog.my_project.data_base_session.ast.command

import gog.my_project.data_base_session.ast.enums.SessionCommandKind
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class SessionAdvisoryLockAstTest {
    @Test
    fun `acquire AST has typed mutable metadata and command kind`() {
        val ast: IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst()
        val command: ISessionCommandAst = ast

        assertIs<AcquireSessionAdvisoryLockAstMetadata>(ast.metadata)
        assertEquals(SessionCommandKind.ACQUIRE_SESSION_ADVISORY_LOCK, command.kind)
        assertNull(ast.metadata.resource)
        assertNull(ast.metadata.waitPolicy)

        ast.metadata.resource = LockResource("application", "migration")
        ast.metadata.waitPolicy = LockWaitPolicy.NoWait
        ast.metadata.resource = LockResource("APPLICATION", "Migration")

        assertEquals(LockResource("APPLICATION", "Migration"), ast.metadata.resource)
        assertEquals(LockWaitPolicy.NoWait, ast.metadata.waitPolicy)
    }

    @Test
    fun `acquire AST accepts injected metadata`() {
        val metadata = AcquireSessionAdvisoryLockAstMetadata().apply {
            resource = LockResource("application", "migration")
            waitPolicy = LockWaitPolicy.NoWait
        }

        val ast = AcquireSessionAdvisoryLockAst(metadata)

        assertSame(metadata, ast.metadata)
    }

    @Test
    fun `release AST has typed mutable metadata and command kind`() {
        val ast: IReleaseSessionAdvisoryLockAst = ReleaseSessionAdvisoryLockAst()
        val command: ISessionCommandAst = ast

        assertIs<ReleaseSessionAdvisoryLockAstMetadata>(ast.metadata)
        assertEquals(SessionCommandKind.RELEASE_SESSION_ADVISORY_LOCK, command.kind)
        assertNull(ast.metadata.resource)

        ast.metadata.resource = LockResource("application", "migration")
        ast.metadata.resource = LockResource("application", "migration-v2")

        assertEquals(LockResource("application", "migration-v2"), ast.metadata.resource)
    }

    @Test
    fun `release AST accepts injected metadata`() {
        val metadata = ReleaseSessionAdvisoryLockAstMetadata().apply {
            resource = LockResource("application", "migration")
        }

        val ast = ReleaseSessionAdvisoryLockAst(metadata)

        assertSame(metadata, ast.metadata)
    }

}
