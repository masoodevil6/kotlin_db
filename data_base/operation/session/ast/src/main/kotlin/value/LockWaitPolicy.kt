package gog.my_project.data_base_session.ast.value

import kotlin.time.Duration

sealed interface LockWaitPolicy {
    data object NoWait : LockWaitPolicy

    class Timeout private constructor(
        val seconds: Int,
    ) : LockWaitPolicy {
        companion object {
            fun of(duration: Duration): Timeout {
                require(duration.isFinite()) { "Lock timeout must be finite." }

                val (seconds, nanoseconds) = duration.toComponents { wholeSeconds, nanos ->
                    wholeSeconds to nanos
                }

                require(nanoseconds == 0) { "Lock timeout must use whole seconds." }
                require(seconds in 1L..300L) { "Lock timeout must be between 1 and 300 seconds." }

                return Timeout(seconds.toInt())
            }
        }
    }
}
