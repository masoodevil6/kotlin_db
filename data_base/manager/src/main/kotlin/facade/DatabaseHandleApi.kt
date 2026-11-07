package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor

/**
 * A typed capability to use one database registration owned by a [DatabaseManagerApi].
 *
 * The handle does not own or expose a JDBC connection. The manager lazily resolves and owns the
 * database context; each call below participates in the manager's operation lease. A scoped query
 * executor is valid only during its callback and must not be retained for later use.
 */
interface DatabaseHandleApi {
    /** The typed key of the registration represented by this handle. */
    val key: DatabaseKey

    /**
     * Opens a non-transactional Connection Scope and provides an executor bound to that scope.
     * Multiple operations through the supplied executor share one Connection, which the scope
     * releases after the callback completes. The executor must not be retained or used afterward.
     * This scope does not provide transaction commit or rollback semantics; resource cleanup and
     * callback-result handling follow the Operation Connection Scope contract. Use only the
     * supplied executor inside the callback; bypass through a root executor is rejected.
     */
    fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T>

    /**
     * Runs work in one Transaction Scope using the Operation connection execution infrastructure.
     * Successful completion commits; a failed query or callback result causes rollback according
     * to the Operation transaction contract. The executor is scoped to the callback and must not
     * be retained or bypassed through a root executor.
     *
     * The resulting [ExecuteResult] preserves operation failures. Ordinary exceptions are
     * represented as Failure and `Error` subclasses propagate after transaction cleanup.
     */
    fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T>

}
