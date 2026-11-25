package gog.my_project.data_base_operation.executor.session.result

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_session.api.result.SessionExecutionResult

class SessionConsumer<R : SessionExecutionResult> internal constructor(
    private val action: () -> ExecuteResult<R>,
) {
    fun execute(): ExecuteResult<R> = action()
}
