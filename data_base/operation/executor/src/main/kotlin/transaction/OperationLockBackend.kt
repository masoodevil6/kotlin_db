package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.dialect.exceptions.SessionRenderException
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.sql.Connection

/**
 * Combines a logical Operation lock resource with its configured database identity.
 * The versioned, length-delimited encoding avoids ambiguous concatenations; SHA-256 keeps the
 * resulting Session resource compact while making accidental collisions impractical.
 */
internal object OperationLockResourceGenerator {
    private const val DOMAIN = "textqb:operation-lock-resource"
    private const val VERSION = 1
    private const val EFFECTIVE_NAMESPACE = "textqb:operation-lock:v1"
    private const val HEX_DIGITS = "0123456789abcdef"

    fun generate(
        identity: DatabaseIdentity,
        logicalResource: LockResource,
    ): LockResource {
        val encoded = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                writeField(output, DOMAIN)
                output.writeInt(VERSION)
                writeField(output, identity.value)
                writeField(output, logicalResource.namespace)
                writeField(output, logicalResource.name)
            }
            buffer.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        val name = buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
        return LockResource(EFFECTIVE_NAMESPACE, name)
    }

    private fun writeField(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }
}

/** Typed observations keep backend uncertainty distinct from a confirmed semantic result. */
internal sealed interface OperationLockAcquireObservation {
    data object Acquired : OperationLockAcquireObservation
    data object TimedOut : OperationLockAcquireObservation
    data class Failed(val failure: Throwable) : OperationLockAcquireObservation
    data class Unknown(val failure: Throwable) : OperationLockAcquireObservation
}

internal sealed interface OperationLockReleaseObservation {
    data object Released : OperationLockReleaseObservation
    data object NotOwned : OperationLockReleaseObservation
    data class Failed(val failure: Throwable) : OperationLockReleaseObservation
    data class Unknown(val failure: Throwable) : OperationLockReleaseObservation
}

internal interface OperationLockBackend {
    fun acquire(
        resource: LockResource,
        waitPolicy: LockWaitPolicy,
    ): OperationLockAcquireObservation

    fun release(resource: LockResource): OperationLockReleaseObservation
}

/** The first concrete adapter reuses the independent Session AST and JDBC executor. */
internal class SessionOperationLockBackend(
    private val connection: Connection,
    private val dialect: DatabaseDialect,
) : OperationLockBackend {
    private val executor = SessionExecutor()

    override fun acquire(
        resource: LockResource,
        waitPolicy: LockWaitPolicy,
    ): OperationLockAcquireObservation {
        val ast: IAcquireSessionAdvisoryLockAst = AcquireSessionAdvisoryLockAst(
            AcquireSessionAdvisoryLockAstMetadata().apply {
                this.resource = resource
                this.waitPolicy = waitPolicy
            },
        )
        return try {
            when (executor.acquire(ast, connection, dialect)) {
                gog.my_project.data_base_session.api.result.AcquireSessionLockResult.Acquired ->
                    OperationLockAcquireObservation.Acquired

                gog.my_project.data_base_session.api.result.AcquireSessionLockResult.TimedOut ->
                    OperationLockAcquireObservation.TimedOut
            }
        } catch (failure: SessionRenderException) {
            OperationLockAcquireObservation.Failed(failure)
        } catch (failure: Throwable) {
            // SessionExecutor exposes no phase marker for execution/cleanup failures. Treating
            // these as unknown prevents the callback from running after a possibly granted lock.
            OperationLockAcquireObservation.Unknown(failure)
        }
    }

    override fun release(resource: LockResource): OperationLockReleaseObservation {
        val ast: IReleaseSessionAdvisoryLockAst = ReleaseSessionAdvisoryLockAst(
            ReleaseSessionAdvisoryLockAstMetadata().apply { this.resource = resource },
        )
        return try {
            when (executor.release(ast, connection, dialect)) {
                gog.my_project.data_base_session.api.result.ReleaseSessionLockResult.Released ->
                    OperationLockReleaseObservation.Released

                gog.my_project.data_base_session.api.result.ReleaseSessionLockResult.NotOwned ->
                    OperationLockReleaseObservation.NotOwned
            }
        } catch (failure: SessionRenderException) {
            OperationLockReleaseObservation.Failed(failure)
        } catch (failure: Throwable) {
            OperationLockReleaseObservation.Unknown(failure)
        }
    }
}
