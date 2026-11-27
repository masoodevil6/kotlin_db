package gog.my_project.data_base_operation.executor.transaction

import java.util.UUID

/** Opaque identity for one admitted root Operation lifecycle. */
internal class OperationId private constructor(
    private val value: UUID,
) {
    fun asString(): String = value.toString()

    override fun equals(other: Any?): Boolean = other is OperationId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        internal fun createRoot(): OperationId = OperationId(UUID.randomUUID())
    }
}
