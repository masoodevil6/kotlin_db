package gog.my_project.data_base_session.executor.exceptions

import java.sql.SQLException

class SessionExecutionException(
    message: String,
    cause: SQLException,
) : RuntimeException(message, cause)
