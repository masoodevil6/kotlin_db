package gog.my_project.data_base.manager.execute.query

import gog.my_project.data_base.core.query.reader.SqlParameter
import java.util.Collections

/** Positional SQL and the exact parameter reference for each placeholder occurrence. */
class BoundQuery(
    val jdbcSql: String,
    parameterOccurrences: List<SqlParameter<*>>,
) {
    /** A stable occurrence sequence whose parameter objects retain their original identities. */
    val parameterOccurrences: List<SqlParameter<*>> =
        Collections.unmodifiableList(ArrayList(parameterOccurrences))
}
