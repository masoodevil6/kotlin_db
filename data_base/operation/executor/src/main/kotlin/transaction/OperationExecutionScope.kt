package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DefaultDatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.TimeoutException

/**
 * Opaque, lifecycle-neutral view of an active Operation scope for operation-family facades.
 * Implementations retain Connection ownership; callers can submit work but cannot obtain JDBC.
 */
interface OperationExecutionContext {
    /** Platform selected for the database whose scope owns this execution. */
    @Suppress("DEPRECATION")
    val databasePlatformConfig: DatabasePlatformConfig
        get() = DefaultDatabaseConfig.config.platform

    fun executeDdl(sql: String, parameters: List<OperationParameter> = emptyList()): ExecuteResult<Boolean>

    /**
     * Executes DDL with an explicit, per-failure policy. Implementations that do not support this
     * capability fail closed through [failed] rather than falling back to unclassified DDL.
     */
    fun executeDdlWithPolicy(
        sql: String,
        parameters: List<OperationParameter>,
        failurePolicy: DdlFailurePolicy,
    ): DdlExecutionOutcome {
        val unsupported = UnsupportedOperationException(
            "Policy-based DDL execution is not supported by this OperationExecutionContext",
        )
        val recorded = failed<Boolean>(unsupported)
        val failure = (recorded as? ExecuteResult.Failure)?.exception ?: unsupported
        return DdlExecutionOutcome.Failure(failure, DdlFailureDisposition.FATAL)
    }

    /**
     * Executes a read on this scope's Connection and lets the caller consume rows while their
     * resources are open. The callback must not retain [OperationQueryRows] after it returns.
     * Implementations that do not support scoped reads fail through the normal scope failure path.
     */
    fun <T> executeRead(
        sql: String,
        parameters: List<OperationParameter> = emptyList(),
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = failed(
        UnsupportedOperationException("Scoped reads are not supported by this OperationExecutionContext"),
    )

    /**
     * Executes a legacy named-parameter SELECT while keeping row access inside the active scope.
     * Existing implementations remain source-compatible through this fail-closed default.
     */
    fun <T> executeSelect(
        query: BuiltQuery,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = failed(
        UnsupportedOperationException("Typed SELECT execution is not supported by this OperationExecutionContext"),
    )

    /** Executes an identity-aware positional SELECT without exposing JDBC ResultSet. */
    fun <T> executeSelect(
        query: BoundQuery,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = failed(
        UnsupportedOperationException("Typed SELECT execution is not supported by this OperationExecutionContext"),
    )

    /** Executes a legacy named-parameter INSERT and returns its generated key. */
    fun executeInsert(query: BuiltQuery): ExecuteResult<Long> = failed(
        UnsupportedOperationException("Typed INSERT execution is not supported by this OperationExecutionContext"),
    )

    /** Executes an identity-aware positional INSERT and returns its generated key. */
    fun executeInsert(query: BoundQuery): ExecuteResult<Long> = failed(
        UnsupportedOperationException("Typed INSERT execution is not supported by this OperationExecutionContext"),
    )

    /** Executes a legacy named-parameter UPDATE and returns affected rows. */
    fun executeUpdate(query: BuiltQuery): ExecuteResult<Int> = failed(
        UnsupportedOperationException("Typed UPDATE execution is not supported by this OperationExecutionContext"),
    )

    /** Executes an identity-aware positional UPDATE and returns affected rows. */
    fun executeUpdate(query: BoundQuery): ExecuteResult<Int> = failed(
        UnsupportedOperationException("Typed UPDATE execution is not supported by this OperationExecutionContext"),
    )

    /** Executes a legacy named-parameter DELETE and returns affected rows. */
    fun executeDelete(query: BuiltQuery): ExecuteResult<Int> = failed(
        UnsupportedOperationException("Typed DELETE execution is not supported by this OperationExecutionContext"),
    )

    /** Executes an identity-aware positional DELETE and returns affected rows. */
    fun executeDelete(query: BoundQuery): ExecuteResult<Int> = failed(
        UnsupportedOperationException("Typed DELETE execution is not supported by this OperationExecutionContext"),
    )

    /** Latches a failure so callers cannot accidentally commit after handling its result. */
    fun <T> failed(failure: Throwable): ExecuteResult<T>
}

/**
 * JDBC-independent, callback-scoped access to query rows and their column labels.
 * Column labels follow the result metadata order, and value access is by SQL column label.
 */
interface OperationQueryRows {
    val columnLabels: List<String>

    fun next(): Boolean
    fun getString(columnLabel: String): String?
    fun getInt(columnLabel: String): Int?
    fun getLong(columnLabel: String): Long?
    fun getBoolean(columnLabel: String): Boolean?
    fun getDouble(columnLabel: String): Double?
    fun getBigDecimal(columnLabel: String): BigDecimal?
    fun getBytes(columnLabel: String): ByteArray?
}

/** A named SQL binding passed across Operation boundaries without exposing JDBC classes. */
data class OperationParameter(
    val name: String,
    val value: Any?,
    val sqlTypeCode: Int,
)

/** Shared state for one synchronous operation scope. The scope owns no JDBC lifecycle by itself. */
class OperationExecutionScope private constructor(
    connection: Connection,
    override val databasePlatformConfig: DatabasePlatformConfig,
    val databaseIdentity: DatabaseIdentity?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit,
) : OperationExecutionContext {

    /** Preserves the established public constructor for manually-created, identity-less scopes. */
    constructor(
        connection: Connection,
        databasePlatformConfig: DatabasePlatformConfig,
    ) : this(connection, databasePlatformConfig, null, Unit)

    /** Internal Operation construction path that carries the registration's destination identity. */
    internal constructor(
        connection: Connection,
        databasePlatformConfig: DatabasePlatformConfig,
        databaseIdentity: DatabaseIdentity?,
    ) : this(connection, databasePlatformConfig, databaseIdentity, Unit)

    @Suppress("DEPRECATION")
    constructor(connection: Connection) : this(connection, DefaultDatabaseConfig.config.platform, null, Unit)

    private val ownerThread = Thread.currentThread()
    private val jdbcConnection = connection
    private val typedQueryExecutor = QueryExecute(jdbcConnection)
    private val laterFailures = mutableListOf<Throwable>()

    @Volatile
    private var active = true

    @Volatile
    private var activeChildScope: OperationExecutionScope? = null

    /** A session lock is a single, non-reentrant lease on this managed Connection Scope. */
    private var sessionAdvisoryLockInProgress = false

    @Volatile
    var firstFailure: Throwable? = null
        private set

    @Volatile
    var fatalError: Error? = null
        private set

    /** Transitional compatibility access used by existing Query, Schema, and Migration executors. */
    @Deprecated("Use OperationExecutionContext.executeRead/executeDdl; direct JDBC access is transitional")
    val connection: Connection
        get() {
            val rejection = rejectOperation()
            check(rejection == null) { rejection?.message ?: "Operation scope is unavailable" }
            return jdbcConnection
        }

    /** Returns and records a lifetime/thread/failure rejection before a scoped operation is run. */
    fun rejectOperation(): Throwable? {
        activeChildScope?.let { child ->
            return child.recordFailure(
                IllegalStateException("Parent Connection Scope cannot execute operations while its child transaction is active"),
            )
        }

        val localRejection = synchronized(this) {
            if (!active) {
                return@synchronized IllegalStateException(
                    "Execution scope has ended; its executor cannot run operations",
                )
            }
            if (Thread.currentThread() !== ownerThread) {
                return@synchronized recordFailureLocked(
                    IllegalStateException("Scoped operations must run on the scope callback thread"),
                )
            }
            firstFailure
        }
        return localRejection ?: OperationScopeGuard.rejectIfNotCurrent(this)
    }

    /**
     * Runs [block] under a deterministic system lock scoped to this Operation's configured
     * database identity. The lock name contains only a versioned hash of the logical lock ID and
     * database identity; acquisition, callback execution, and release use this same root Scope.
     *
     * Lock IDs accepted here are 1–128 ASCII characters matching
     * `[A-Za-z0-9][A-Za-z0-9._:-]*`. This stricter execution-time validation preserves the
     * existing [SystemLock] constructor contract while rejecting ambiguous identifiers.
     */
    fun <T> withSystemLock(
        systemLock: SystemLock,
        timeoutSeconds: Int,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        rejectOperation()?.let { return ExecuteResult.Failure(it) }
        if (!isValidSystemLockIdentifier(systemLock.id)) {
            return failed(
                IllegalArgumentException(
                    "System lock identifiers must be 1-128 ASCII letters, digits, '.', '_', ':' or '-'",
                ),
            )
        }
        val identity = databaseIdentity ?: return failed(
            IllegalStateException("A canonical DatabaseIdentity is required to run a system lock"),
        )

        val lockName = try {
            systemLockName(systemLock, identity)
        } catch (error: Error) {
            if (fatalError !== error) recordFatal(error)
            throw error
        } catch (failure: Exception) {
            return failed(failure)
        }

        return withSessionAdvisoryLock(lockName, timeoutSeconds, block)
    }

    /**
     * Runs [block] while holding a MySQL-family session advisory lock on this Operation-owned
     * Connection. The callback runs synchronously, exactly once, and only after acquisition.
     * The lock is released before this method returns, including when the scope has latched a
     * failure. This capability is restricted to a managed root Connection Scope; transaction
     * child scopes and directly constructed, unmanaged scopes fail closed.
     */
    fun <T> withSessionAdvisoryLock(
        lockName: String,
        timeoutSeconds: Int,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        rejectOperation()?.let { return ExecuteResult.Failure(it) }
        if (!OperationScopeGuard.isManagedConnectionScope(this)) {
            return failed(
                IllegalStateException(
                    "Session advisory locks require the active Operation-managed Connection Scope",
                ),
            )
        }
        if (!isValidSessionAdvisoryLockName(lockName)) {
            return failed(
                IllegalArgumentException("Session advisory lock names must be non-blank printable ASCII and at most 64 characters"),
            )
        }
        if (timeoutSeconds !in 0..MAX_SESSION_ADVISORY_LOCK_TIMEOUT_SECONDS) {
            return failed(
                IllegalArgumentException(
                    "Session advisory lock timeout must be between 0 and $MAX_SESSION_ADVISORY_LOCK_TIMEOUT_SECONDS seconds",
                ),
            )
        }
        when (databasePlatformConfig.dialect) {
            DialectQuery.MY_SQL, DialectQuery.MARIA_DB -> Unit
            else -> return failed(
                UnsupportedOperationException(
                    "Session advisory locks are not supported for dialect ${databasePlatformConfig.dialect::class.simpleName}",
                ),
            )
        }

        val alreadyRunning = synchronized(this) {
            if (sessionAdvisoryLockInProgress) {
                true
            } else {
                sessionAdvisoryLockInProgress = true
                false
            }
        }
        if (alreadyRunning) {
            return failed(IllegalStateException("Nested or reentrant session advisory locks are not supported"))
        }

        var acquired = false
        var outcome: ExecuteResult<T>? = null
        var fatal: Error? = null

        try {
            val acquireResult = executeSelect(
                BoundQuery(
                    "SELECT GET_LOCK(?, ?) AS acquired",
                    listOf(
                        QueryParameterBinding("lockName", lockName, Types.VARCHAR),
                        QueryParameterBinding("timeoutSeconds", timeoutSeconds, Types.INTEGER),
                    ),
                ),
            ) { rows ->
                if (!rows.next()) {
                    throw IllegalStateException("GET_LOCK returned no result row")
                }
                val acquiredValue = rows.getInt("acquired")
                // Set this as soon as a successful grant is observed so statement/ResultSet close
                // failure still triggers the matching RELEASE_LOCK cleanup.
                if (acquiredValue == 1) acquired = true
                if (rows.next()) {
                    throw IllegalStateException("GET_LOCK returned more than one result row")
                }
                acquiredValue
            }

            if (!acquired) {
                outcome = when (acquireResult) {
                    is ExecuteResult.Failure -> acquireResult
                    is ExecuteResult.Success -> when (acquireResult.result) {
                        0 -> failed(TimeoutException("Timed out acquiring session advisory lock '$lockName'"))
                        null -> failed(IllegalStateException("GET_LOCK returned SQL NULL for '$lockName'"))
                        else -> failed(
                            IllegalStateException(
                                "GET_LOCK returned unexpected result '${acquireResult.result}' for '$lockName'",
                            ),
                        )
                    }
                }
            } else if (acquireResult is ExecuteResult.Failure) {
                // The server granted the lock, but the SELECT path later failed (for example while
                // closing JDBC resources). Do not run user work; release the confirmed grant below.
                outcome = acquireResult
            } else {
                try {
                    val callbackResult = block(this)
                    recordResult(callbackResult)
                    val callbackError = (callbackResult as? ExecuteResult.Failure)?.exception as? Error
                    if (callbackError != null) {
                        fatal = callbackError
                        if (fatalError !== callbackError) recordFatal(callbackError)
                        outcome = ExecuteResult.Failure(callbackError)
                    } else {
                        outcome = firstFailure?.let { ExecuteResult.Failure(it) } ?: callbackResult
                    }
                } catch (error: Error) {
                    fatal = error
                    if (fatalError !== error) recordFatal(error)
                    outcome = ExecuteResult.Failure(error)
                } catch (failure: Throwable) {
                    val primary = if (hasRecordedFailure(failure)) firstFailure ?: failure else recordFailure(failure)
                    if (primary !== failure) addSuppressedSafely(primary, failure)
                    outcome = ExecuteResult.Failure(primary)
                }
            }
        } catch (error: Error) {
            fatal = error
            if (fatalError !== error) recordFatal(error)
            outcome = ExecuteResult.Failure(error)
        } catch (failure: Throwable) {
            val primary = if (hasRecordedFailure(failure)) firstFailure ?: failure else recordFailure(failure)
            if (primary !== failure) addSuppressedSafely(primary, failure)
            outcome = ExecuteResult.Failure(primary)
        }

        if (acquired) {
            try {
                val releaseResult = executeSessionLockCleanupSelect(
                    BoundQuery(
                        "SELECT RELEASE_LOCK(?) AS released",
                        listOf(QueryParameterBinding("lockName", lockName, Types.VARCHAR)),
                    ),
                ) { rows ->
                    if (!rows.next()) {
                        throw IllegalStateException("RELEASE_LOCK returned no result row")
                    }
                    val releasedValue = rows.getInt("released")
                    if (rows.next()) {
                        throw IllegalStateException("RELEASE_LOCK returned more than one result row")
                    }
                    releasedValue
                }

                val releaseFailure = when (releaseResult) {
                    is ExecuteResult.Failure -> releaseResult.exception
                    is ExecuteResult.Success -> when (releaseResult.result) {
                        1 -> null
                        0 -> IllegalStateException("RELEASE_LOCK did not own session advisory lock '$lockName'")
                        null -> IllegalStateException("RELEASE_LOCK returned SQL NULL for '$lockName'")
                        else -> IllegalStateException(
                            "RELEASE_LOCK returned unexpected result '${releaseResult.result}' for '$lockName'",
                        )
                    }
                }
                if (releaseFailure != null) {
                    outcome = mergeSessionLockCleanupFailure(outcome, releaseFailure)
                }
            } catch (error: Error) {
                if (fatal == null) {
                    fatal = error
                    if (fatalError !== error) recordFatal(error)
                } else {
                    addSuppressedSafely(fatal!!, error)
                    if (fatalError !== error) recordFatal(error)
                }
                outcome = ExecuteResult.Failure(fatal!!)
            } catch (failure: Throwable) {
                outcome = mergeSessionLockCleanupFailure(outcome, failure)
            }
        }

        synchronized(this) { sessionAdvisoryLockInProgress = false }
        fatal?.let { throw it }
        return outcome ?: ExecuteResult.Failure(
            recordFailure(IllegalStateException("Session advisory lock execution completed without a result")),
        )
    }

    /**
     * Starts a transaction using this managed Connection Scope's Connection. The returned child
     * scope is the only valid execution context inside [block]; the Connection remains owned by
     * this parent scope and is not closed when the child transaction completes.
     */
    fun <T> transaction(
        block: (transactionScope: OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = OperationTransactionExecutor.transactionOn(this, block)

    fun recordResult(result: ExecuteResult<*>) {
        if (result is ExecuteResult.Failure) recordFailure(result.exception)
    }

    fun recordFailure(failure: Throwable): Throwable {
        activeChildScope?.let { child -> return child.recordFailure(failure) }
        return synchronized(this) {
            if (!active) return@synchronized failure
            recordFailureLocked(failure)
        }
    }

    private fun recordFailureLocked(failure: Throwable): Throwable {
        val currentPrimary = firstFailure
        if (currentPrimary == null) {
            firstFailure = failure
        } else if (currentPrimary !== failure && failure !is Error) {
            if (fatalError != null) {
                addSuppressedSafely(fatalError!!, failure)
            } else if (laterFailures.none { it === failure }) {
                laterFailures += failure
            }
        }

        if (failure is Error) {
            recordFatalLocked(failure)
        } else {
            fatalError?.let { addSuppressedSafely(it, failure) }
        }
        return firstFailure ?: failure
    }

    fun recordFatal(error: Error) {
        activeChildScope?.let { child ->
            child.recordFatal(error)
            return
        }
        synchronized(this) {
            if (!active) return
            recordFatalLocked(error)
        }
    }

    private fun recordFatalLocked(error: Error) {
        val currentFatal = fatalError
        if (currentFatal == null) {
            fatalError = error
            firstFailure?.let { prior ->
                addSuppressedSafely(error, prior)
                prior.suppressed.forEach { nested -> addSuppressedSafely(error, nested) }
            }
            laterFailures.forEach { later -> addSuppressedSafely(error, later) }
            laterFailures.clear()
        } else if (currentFatal !== error) {
            addSuppressedSafely(currentFatal, error)
        }
        if (firstFailure == null) firstFailure = error
    }

    /** Seals the callback phase. Lifecycle code may still use the private connection for cleanup. */
    @Deprecated("Scope lifecycle is owned by Operation executors")
    fun end() {
        synchronized(this) {
            if (!active) return
            val misuse = IllegalStateException("Execution scope lifecycle is owned by Operation")
            recordFailureLocked(misuse)
        }
    }

    internal fun endForLifecycle() {
        synchronized(this) { active = false }
    }

    internal fun lifecycleConnection(): Connection = jdbcConnection

    internal fun suspendForChild(child: OperationExecutionScope) {
        synchronized(this) {
            check(active) { "Execution scope has ended; it cannot create a child transaction" }
            check(activeChildScope == null) { "A child transaction is already active for this Connection Scope" }
            activeChildScope = child
        }
    }

    internal fun restoreAfterChild(child: OperationExecutionScope) {
        synchronized(this) {
            check(activeChildScope === child) { "Only the active child can restore its parent Connection Scope" }
            activeChildScope = null
        }
    }

    override fun executeDdl(
        sql: String,
        parameters: List<OperationParameter>,
    ): ExecuteResult<Boolean> {
        val rejection = rejectOperation()
        if (rejection != null) return ExecuteResult.Failure(rejection)

        return try {
            val completed = executeDdlQuery(sql, parameters)
            recordResult(completed)
            completed
        } catch (error: Error) {
            recordFatal(error)
            throw error
        } catch (exception: Exception) {
            failed(exception)
        } catch (failure: Throwable) {
            failed(failure)
        }
    }

    /** Invokes a legacy injected executor without exposing this scope's owned Connection to callers. */
    fun executeDdl(query: BuiltQuery, queryExecutor: IQueryExecute): ExecuteResult<Boolean> =
        executeDml(
            queryType = "Scoped DDL BuiltQuery",
            executionExecutor = queryExecutor,
            missingResultMessage = "Schema query executor completed without delivering an execution result",
        ) { executor, callback ->
            executor.executeTable(jdbcConnection, query, callback)
        }

    override fun <T> executeSelect(
        query: BuiltQuery,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = executeSelectQuery("BuiltQuery", read) { executor, callback ->
        executor.executeSelect(query, callback)
    }

    override fun <T> executeSelect(
        query: BoundQuery,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = executeSelectQuery("BoundQuery", read) { executor, callback ->
        executor.executeSelect(query, callback)
    }

    override fun executeInsert(query: BuiltQuery): ExecuteResult<Long> = executeDml("INSERT BuiltQuery") { executor, callback ->
        executor.executeInsert(query, callback)
    }

    override fun executeInsert(query: BoundQuery): ExecuteResult<Long> = executeDml("INSERT BoundQuery") { executor, callback ->
        executor.executeInsert(query, callback)
    }

    override fun executeUpdate(query: BuiltQuery): ExecuteResult<Int> = executeDml("UPDATE BuiltQuery") { executor, callback ->
        executor.executeUpdate(query, callback)
    }

    override fun executeUpdate(query: BoundQuery): ExecuteResult<Int> = executeDml("UPDATE BoundQuery") { executor, callback ->
        executor.executeUpdate(query, callback)
    }

    override fun executeDelete(query: BuiltQuery): ExecuteResult<Int> = executeDml("DELETE BuiltQuery") { executor, callback ->
        executor.executeDelete(query, callback)
    }

    override fun executeDelete(query: BoundQuery): ExecuteResult<Int> = executeDml("DELETE BoundQuery") { executor, callback ->
        executor.executeDelete(query, callback)
    }

    private fun <T> executeSelectQuery(
        queryType: String,
        read: (OperationQueryRows) -> T,
        resultDescription: String = "$queryType SELECT",
        scopeRejection: () -> Throwable? = { rejectOperation() },
        preserveLocalFailure: Boolean = false,
        execute: (IQueryExecute, (ExecuteResult<ResultSet>) -> Unit) -> Unit,
    ): ExecuteResult<T> {
        val rejection = scopeRejection()
        if (rejection != null) return ExecuteResult.Failure(rejection)

        var deliveredFailure: Throwable? = null
        var deliveredFailureRecorded = false
        return try {
            var callbackCount = 0
            var result: ExecuteResult<T>? = null
            execute(typedQueryExecutor) callback@{ selected ->
                callbackCount++
                if (callbackCount > 1) {
                    val duplicateCallback = IllegalStateException(
                        "$resultDescription execution delivered more than one result",
                    )
                    result = ExecuteResult.Failure(recordSelectFailure(duplicateCallback, preserveLocalFailure))
                    return@callback
                }

                result = when (selected) {
                    is ExecuteResult.Failure -> {
                        // Latch the database failure before Statement.use can report a cleanup
                        // failure, so the original SQL failure remains primary.
                        deliveredFailure = selected.exception
                        recordResult(selected)
                        deliveredFailureRecorded = true
                        selected
                    }
                    is ExecuteResult.Success -> {
                        val resultSet = selected.result
                        if (resultSet == null) {
                            ExecuteResult.Failure(
                                IllegalStateException("$resultDescription completed without a row result"),
                            )
                        } else {
                            resultSet.use { rows ->
                                val scopedRows = JdbcOperationQueryRows(
                                    rows,
                                    ownerThread,
                                    scopeRejection,
                                ) { failure -> recordFailure(failure) }
                                try {
                                    ExecuteResult.Success(read(scopedRows))
                                } finally {
                                    scopedRows.invalidate()
                                }
                            }
                        }
                    }
                }
            }

            if (callbackCount == 0) {
                val missingResult = IllegalStateException("$resultDescription execution completed without a result")
                ExecuteResult.Failure(recordSelectFailure(missingResult, preserveLocalFailure))
            } else {
                val completed = result ?: ExecuteResult.Failure(
                    recordSelectFailure(
                        IllegalStateException("$resultDescription execution produced no result"),
                        preserveLocalFailure,
                    ),
                )
                if (!deliveredFailureRecorded) recordResult(completed)
                completed
            }
        } catch (error: Error) {
            recordFatal(error)
            throw error
        } catch (exception: Exception) {
            val primary = deliveredFailure
            if (primary != null && primary !== exception) {
                addSuppressedSafely(primary, exception)
                val recorded = recordFailure(exception)
                ExecuteResult.Failure(if (preserveLocalFailure) primary else recorded)
            } else {
                ExecuteResult.Failure(recordSelectFailure(exception, preserveLocalFailure))
            }
        } catch (failure: Throwable) {
            val primary = deliveredFailure
            if (primary != null && primary !== failure) {
                addSuppressedSafely(primary, failure)
                val recorded = recordFailure(failure)
                ExecuteResult.Failure(if (preserveLocalFailure) primary else recorded)
            } else {
                ExecuteResult.Failure(recordSelectFailure(failure, preserveLocalFailure))
            }
        }
    }

    private fun <T> executeSessionLockCleanupSelect(
        query: BoundQuery,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = executeSelectQuery(
        queryType = "Session advisory lock cleanup",
        read = read,
        resultDescription = "Session advisory lock cleanup SELECT",
        scopeRejection = ::rejectCleanupOperation,
        preserveLocalFailure = true,
    ) { executor, callback ->
        executor.executeSelect(query, callback)
    }

    private fun recordSelectFailure(failure: Throwable, preserveLocalFailure: Boolean): Throwable {
        if (!preserveLocalFailure) return recordFailure(failure)
        if (!hasRecordedFailure(failure)) recordFailure(failure)
        return failure
    }

    /** Cleanup may ignore a latched failure, but may not ignore scope, thread, or child lifecycle. */
    private fun rejectCleanupOperation(): Throwable? {
        activeChildScope?.let { child ->
            return child.recordFailure(
                IllegalStateException("Cannot release a session advisory lock while a child transaction is active"),
            )
        }
        val localRejection = synchronized(this) {
            when {
                !active -> IllegalStateException("Execution scope has ended; lock cleanup is unavailable")
                Thread.currentThread() !== ownerThread -> recordFailureLocked(
                    IllegalStateException("Session advisory lock cleanup must run on the scope callback thread"),
                )
                else -> null
            }
        }
        return localRejection ?: OperationScopeGuard.rejectIfNotCurrent(this)
    }

    private fun <T> mergeSessionLockCleanupFailure(
        currentOutcome: ExecuteResult<T>?,
        cleanupFailure: Throwable,
    ): ExecuteResult<T> {
        val currentFatal = fatalError
        if (currentFatal != null) {
            addSuppressedSafely(currentFatal, cleanupFailure)
            if (!hasRecordedFailure(cleanupFailure)) recordFailure(cleanupFailure)
            return ExecuteResult.Failure(currentFatal)
        }

        val primary = (currentOutcome as? ExecuteResult.Failure)?.exception
        if (primary != null) {
            addSuppressedSafely(primary, cleanupFailure)
            if (!hasRecordedFailure(cleanupFailure)) recordFailure(cleanupFailure)
            return ExecuteResult.Failure(primary)
        }

        val recorded = if (hasRecordedFailure(cleanupFailure)) {
            firstFailure ?: cleanupFailure
        } else {
            recordFailure(cleanupFailure)
        }
        return ExecuteResult.Failure(recorded)
    }

    private fun hasRecordedFailure(failure: Throwable): Boolean =
        firstFailure === failure || laterFailures.any { it === failure } || fatalError === failure

    private fun isValidSessionAdvisoryLockName(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_SESSION_ADVISORY_LOCK_NAME_LENGTH &&
            value.all { it.code in MIN_PRINTABLE_ASCII..MAX_PRINTABLE_ASCII }

    private fun isValidSystemLockIdentifier(value: String): Boolean =
        SYSTEM_LOCK_IDENTIFIER.matches(value)

    private fun systemLockName(systemLock: SystemLock, identity: DatabaseIdentity): String {
        val encodedInput = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                writeSystemLockField(output, SYSTEM_LOCK_HASH_DOMAIN)
                output.writeInt(SYSTEM_LOCK_HASH_VERSION)
                writeSystemLockField(output, systemLock.id)
                writeSystemLockField(output, identity.value)
            }
        }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedInput)
        val hex = digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return SYSTEM_LOCK_NAME_PREFIX + hex.take(SYSTEM_LOCK_HASH_LENGTH)
    }

    private fun writeSystemLockField(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private companion object {
        const val MAX_SESSION_ADVISORY_LOCK_NAME_LENGTH = 64
        const val MAX_SESSION_ADVISORY_LOCK_TIMEOUT_SECONDS = 300
        const val MIN_PRINTABLE_ASCII = 0x20
        const val MAX_PRINTABLE_ASCII = 0x7E
        const val SYSTEM_LOCK_HASH_DOMAIN = "textqb.system-lock"
        const val SYSTEM_LOCK_HASH_VERSION = 1
        const val SYSTEM_LOCK_NAME_PREFIX = "textqb:syslock:v1:"
        const val SYSTEM_LOCK_HASH_LENGTH = 46
        val SYSTEM_LOCK_IDENTIFIER = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }

    private fun <T> executeDml(
        queryType: String,
        executionExecutor: IQueryExecute = typedQueryExecutor,
        missingResultMessage: String = "$queryType execution completed without a result",
        execute: (IQueryExecute, (ExecuteResult<T>) -> Unit) -> Unit,
    ): ExecuteResult<T> {
        val rejection = rejectOperation()
        if (rejection != null) return ExecuteResult.Failure(rejection)

        return try {
            var callbackCount = 0
            var result: ExecuteResult<T>? = null
            execute(executionExecutor) { delivered ->
                callbackCount++
                if (callbackCount == 1) {
                    result = delivered
                    recordResult(delivered)
                } else {
                    val duplicateCallback = IllegalStateException(
                        "$queryType execution delivered more than one result",
                    )
                    result = ExecuteResult.Failure(recordFailure(duplicateCallback))
                }
            }

            if (callbackCount == 0) {
                ExecuteResult.Failure(
                    recordFailure(
                        IllegalStateException(missingResultMessage),
                    ),
                )
            } else {
                result ?: ExecuteResult.Failure(
                    recordFailure(IllegalStateException("$queryType execution produced no result")),
                )
            }
        } catch (error: Error) {
            recordFatal(error)
            throw error
        } catch (exception: Exception) {
            failed(exception)
        } catch (failure: Throwable) {
            failed(failure)
        }
    }

    override fun executeDdlWithPolicy(
        sql: String,
        parameters: List<OperationParameter>,
        failurePolicy: DdlFailurePolicy,
    ): DdlExecutionOutcome {
        val rejection = rejectOperation()
        if (rejection != null) return DdlExecutionOutcome.Failure(rejection, DdlFailureDisposition.FATAL)

        val completed = try {
            executeDdlQuery(sql, parameters)
        } catch (error: Error) {
            recordFatal(error)
            throw error
        } catch (exception: Exception) {
            return classifyDdlFailure(exception, failurePolicy)
        } catch (failure: Throwable) {
            return classifyDdlFailure(failure, failurePolicy)
        }

        return when (completed) {
            is ExecuteResult.Success -> {
                val value = completed.result
                if (value != null) {
                    DdlExecutionOutcome.Success(value)
                } else {
                    val missingValue = IllegalStateException("Scoped DDL completed without a success value")
                    DdlExecutionOutcome.Failure(recordFailure(missingValue), DdlFailureDisposition.FATAL)
                }
            }
            is ExecuteResult.Failure -> classifyDdlFailure(completed.exception, failurePolicy)
        }
    }

    private fun executeDdlQuery(
        sql: String,
        parameters: List<OperationParameter>,
    ): ExecuteResult<Boolean> {
        var result: ExecuteResult<Boolean>? = null
        val query = BuiltQuery(
            sql,
            parameters.map { QueryParameterBinding(it.name, it.value, it.sqlTypeCode) }.toMutableList(),
        )
        var deliveredFailure: Throwable? = null
        try {
            typedQueryExecutor.executeTable(query) { executed ->
                result = executed
                deliveredFailure = (executed as? ExecuteResult.Failure)?.exception
            }
        } catch (error: Error) {
            deliveredFailure?.let { addSuppressedSafely(error, it) }
            throw error
        } catch (failure: Throwable) {
            val primary = deliveredFailure
            if (primary != null && primary !== failure) {
                addSuppressedSafely(primary, failure)
                return ExecuteResult.Failure(primary)
            }
            throw failure
        }
        return result ?: ExecuteResult.Failure(
            IllegalStateException("Scoped DDL execution completed without a result"),
        )
    }

    private fun classifyDdlFailure(
        failure: Throwable,
        failurePolicy: DdlFailurePolicy,
    ): DdlExecutionOutcome {
        if (failure is Error) {
            recordFatal(failure)
            throw failure
        }

        val disposition = try {
            requireNotNull(failurePolicy.classify(failure)) {
                "DDL failure policy returned no disposition"
            }
        } catch (classifierFailure: Error) {
            recordFailure(failure)
            recordFatal(classifierFailure)
            addSuppressedSafely(classifierFailure, failure)
            throw classifierFailure
        } catch (classifierFailure: Throwable) {
            addSuppressedSafely(failure, classifierFailure)
            val first = recordFailure(failure)
            return DdlExecutionOutcome.Failure(first, DdlFailureDisposition.FATAL)
        }

        return when (disposition) {
            DdlFailureDisposition.RECOVERABLE ->
                DdlExecutionOutcome.Failure(failure, DdlFailureDisposition.RECOVERABLE)
            DdlFailureDisposition.FATAL ->
                DdlExecutionOutcome.Failure(recordFailure(failure), DdlFailureDisposition.FATAL)
        }
    }

    override fun <T> executeRead(
        sql: String,
        parameters: List<OperationParameter>,
        read: (OperationQueryRows) -> T,
    ): ExecuteResult<T> = executeSelectQuery(
        queryType = "Scoped read",
        read = read,
        resultDescription = "Scoped read",
    ) { executor, callback ->
        val query = BuiltQuery(
            sql,
            parameters.map { QueryParameterBinding(it.name, it.value, it.sqlTypeCode) }.toMutableList(),
        )
        executor.executeSelect(query, callback)
    }

    override fun <T> failed(failure: Throwable): ExecuteResult<T> =
        ExecuteResult.Failure(recordFailure(failure))

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary && primary.suppressed.none { it === secondary }) {
            primary.addSuppressed(secondary)
        }
    }

}

/** Bridges the JDBC cursor only inside OperationExecutionScope.executeRead. */
private class JdbcOperationQueryRows(
    private val resultSet: ResultSet,
    private val ownerThread: Thread,
    private val scopeRejection: () -> Throwable?,
    private val onThreadViolation: (Throwable) -> Unit,
) : OperationQueryRows {
    @Volatile
    private var active = true

    override val columnLabels: List<String> by lazy {
        ensureActive()
        val metadata = resultSet.metaData
        (1..metadata.columnCount).map { index ->
            metadata.getColumnLabel(index).takeIf(String::isNotBlank) ?: metadata.getColumnName(index)
        }
    }

    override fun next(): Boolean {
        ensureActive()
        return resultSet.next()
    }

    override fun getString(columnLabel: String): String? {
        ensureActive()
        return resultSet.getString(columnLabel)
    }

    override fun getInt(columnLabel: String): Int? {
        ensureActive()
        val value = resultSet.getInt(columnLabel)
        return if (resultSet.wasNull()) null else value
    }

    override fun getLong(columnLabel: String): Long? {
        ensureActive()
        val value = resultSet.getLong(columnLabel)
        return if (resultSet.wasNull()) null else value
    }

    override fun getBoolean(columnLabel: String): Boolean? {
        ensureActive()
        val value = resultSet.getBoolean(columnLabel)
        return if (resultSet.wasNull()) null else value
    }

    override fun getDouble(columnLabel: String): Double? {
        ensureActive()
        val value = resultSet.getDouble(columnLabel)
        return if (resultSet.wasNull()) null else value
    }

    override fun getBigDecimal(columnLabel: String): BigDecimal? {
        ensureActive()
        return resultSet.getBigDecimal(columnLabel)
    }

    override fun getBytes(columnLabel: String): ByteArray? {
        ensureActive()
        return resultSet.getBytes(columnLabel)
    }

    fun invalidate() {
        active = false
    }

    private fun ensureActive() {
        check(active) { "Scoped query rows are only available inside the executeRead callback" }
        if (Thread.currentThread() !== ownerThread) {
            val failure = IllegalStateException("Scoped query rows must be consumed on the executeRead callback thread")
            onThreadViolation(failure)
            throw failure
        }
        scopeRejection()?.let { rejection ->
            throw IllegalStateException("Scoped query rows are unavailable after an Operation failure", rejection)
        }
    }
}

/** Prevents nested managed scopes across Query, Schema, and Migration on one callback thread. */
internal object OperationScopeGuard {
    private class State {
        var scope: OperationExecutionScope? = null
        var scopeKind: ScopeKind? = null
        var childParent: OperationExecutionScope? = null
        var pendingFailure: Throwable? = null
    }

    private enum class ScopeKind { CONNECTION, TRANSACTION }

    private val current = ThreadLocal<State?>()

    /** True only for the active root Connection Scope owned by an Operation lifecycle. */
    internal fun isManagedConnectionScope(scope: OperationExecutionScope): Boolean {
        val state = current.get() ?: return false
        return state.scope === scope && state.scopeKind == ScopeKind.CONNECTION && state.childParent == null
    }

    /** Claims the current thread for a managed scope, or latches a nested-scope rejection. */
    internal fun enter(): Throwable? {
        val state = current.get()
        if (state != null) {
            val failure = IllegalStateException("Nested managed operation scopes are not supported")
            val activeScope = state.scope
            if (activeScope != null) return activeScope.recordFailure(failure)
            if (state.pendingFailure == null) state.pendingFailure = failure
            return state.pendingFailure ?: failure
        }
        current.set(State())
        return null
    }

    /** Publishes the callback scope and transfers any rejection seen while its connection was starting. */
    internal fun activate(scope: OperationExecutionScope) {
        val state = checkNotNull(current.get()) { "Operation scope was not entered" }
        check(state.scope == null) { "Operation scope is already active" }
        state.scope = scope
        state.scopeKind = ScopeKind.CONNECTION
        state.pendingFailure?.let(scope::recordFailure)
        state.pendingFailure = null
    }

    /** Publishes a root transaction scope, which cannot parent another transaction. */
    internal fun activateTransaction(scope: OperationExecutionScope) {
        val state = checkNotNull(current.get()) { "Operation scope was not entered" }
        check(state.scope == null) { "Operation scope is already active" }
        state.scope = scope
        state.scopeKind = ScopeKind.TRANSACTION
        state.pendingFailure?.let(scope::recordFailure)
        state.pendingFailure = null
    }

    /** Temporarily suspends a managed Connection Scope while its child transaction is active. */
    internal fun enterChildTransaction(
        parent: OperationExecutionScope,
        child: OperationExecutionScope,
    ): Throwable? {
        val state = current.get()
            ?: return IllegalStateException("A child transaction requires an active managed Connection Scope")
        if (state.scope !== parent || state.scopeKind != ScopeKind.CONNECTION || state.childParent != null) {
            val failure = IllegalStateException(
                "A child transaction can only start from the currently active managed Connection Scope",
            )
            val activeScope = state.scope
            if (activeScope != null) return activeScope.recordFailure(failure)
            if (state.pendingFailure == null) state.pendingFailure = failure
            return state.pendingFailure ?: failure
        }

        parent.suspendForChild(child)
        state.childParent = parent
        state.scope = child
        state.scopeKind = ScopeKind.TRANSACTION
        return null
    }

    /** Restores the parent only after child transaction cleanup has completed. */
    internal fun exitChildTransaction(
        parent: OperationExecutionScope,
        child: OperationExecutionScope,
    ) {
        val state = checkNotNull(current.get()) { "Child transaction guard is unavailable" }
        check(state.scope === child && state.childParent === parent) {
            "Only the active child transaction can restore its parent Connection Scope"
        }
        state.scope = parent
        state.scopeKind = ScopeKind.CONNECTION
        state.childParent = null
        parent.restoreAfterChild(child)
    }

    /** Prevents a suspended parent or stale scope from executing while a child is active. */
    internal fun rejectIfNotCurrent(scope: OperationExecutionScope): Throwable? {
        val state = current.get() ?: return null
        val activeScope = state.scope ?: return null
        if (activeScope === scope) return null

        val message = if (state.childParent === scope) {
            "Parent Connection Scope cannot execute operations while its child transaction is active"
        } else {
            "Only the current managed Operation scope can execute operations"
        }
        return activeScope.recordFailure(IllegalStateException(message))
    }

    internal fun exit() {
        current.remove()
    }
}
