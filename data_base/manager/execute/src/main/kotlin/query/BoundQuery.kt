package gog.my_project.data_base.manager.execute.query

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import java.util.Collections

/** Positional SQL and the exact parameter reference for each placeholder occurrence. */
class BoundQuery(
    val jdbcSql: String,
    parameterOccurrences: List<out IQueryParameterBinding>,
) {
    /** A stable occurrence sequence whose binding objects retain their original identities. */
    val parameterOccurrences: List<IQueryParameterBinding> =
        Collections.unmodifiableList(ArrayList(parameterOccurrences))
}
