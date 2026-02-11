package gog.my_project.data_base.manager.execute.tools

import java.sql.ResultSet
import java.sql.SQLException

sealed class ExecuteResult {

    data class SuccessExecute(val result: ResultSet?) : ExecuteResult();

    data class ErrorExecute(val exception: SQLException) : ExecuteResult();

    data class Error(val error: String) : ExecuteResult();

}