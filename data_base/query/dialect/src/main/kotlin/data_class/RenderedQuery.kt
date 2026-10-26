package gog.my_project.data_base.query.dialect.data_class

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding

/** Named display SQL and positional SQL rendered from the same SELECT AST. */
data class RenderedQuery(
    val displaySql: String,
    val jdbcSql: String,
    val parameterOccurrences: List<IQueryParameterBinding>,
)
