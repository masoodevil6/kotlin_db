package gog.my_project.data_base_session.api.result

sealed interface SessionExecutionResult

sealed interface AcquireSessionLockResult : SessionExecutionResult {
    data object Acquired : AcquireSessionLockResult
    data object TimedOut : AcquireSessionLockResult
}

sealed interface ReleaseSessionLockResult : SessionExecutionResult {
    data object Released : ReleaseSessionLockResult
    data object NotOwned : ReleaseSessionLockResult
}
