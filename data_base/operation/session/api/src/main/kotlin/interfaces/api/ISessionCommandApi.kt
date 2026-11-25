package gog.my_project.data_base_session.api.interfaces.api

import gog.my_project.data_base_session.api.interfaces.executor.ISessionCommandExecutor
import gog.my_project.data_base_session.api.result.SessionExecutionResult
import gog.my_project.data_base_session.ast.interfaces.ISessionCommandAst

interface ISessionCommandApi<
    A : ISessionCommandAst,
    R : SessionExecutionResult,
> : ISessionApi<A> {
    fun dispatch(executor: ISessionCommandExecutor): R
}
