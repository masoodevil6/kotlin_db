package gog.my_project.data_base.query.executer.manager

/** A JDBC commit failed; the commit outcome may be unknown and rollback was attempted. */
class TransactionCommitException(cause: Throwable) :
    IllegalStateException(
        "JDBC commit failed; rollback was attempted and the commit outcome may be unknown",
        cause,
    )

/** Reports a connection close failure without hiding whether the transaction committed. */
class TransactionCompletionException(
    val committed: Boolean,
    cause: Exception,
) : IllegalStateException(
    if (committed) {
        "The transaction committed, but closing its connection failed; do not retry as an uncommitted transaction"
    } else {
        "The transaction did not commit, but closing its connection failed"
    },
    cause,
)

/** Fatal close failure after a successful commit, retaining the committed outcome explicitly. */
class TransactionCompletionError(
    val committed: Boolean,
    cause: Error,
) : Error(
    if (committed) {
        "The transaction committed, but closing its connection failed; do not retry as an uncommitted transaction"
    } else {
        "The transaction did not commit, but closing its connection failed"
    },
    cause,
)
