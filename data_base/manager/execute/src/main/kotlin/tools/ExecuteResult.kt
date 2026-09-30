package gog.my_project.data_base.manager.execute.tools

sealed class ExecuteResult<out T> {

    data class Success<out T>(val result: T?) : ExecuteResult<T>();

    data class Failure(val exception: Throwable) : ExecuteResult<Nothing>();

}