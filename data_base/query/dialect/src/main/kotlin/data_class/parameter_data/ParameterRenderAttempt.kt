package gog.my_project.data_base.query.dialect.data_class.parameter_data

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding

/** Result of inspecting parameter sources emitted by the named SQL render pass. */
data class ParameterRenderAttempt(
    val namedSql: String?,
    val positionalSql: String?,
    val parameterOccurrences: List<IQueryParameterBinding>,
    val parameterAstOccurrenceCount: Int,
    val legacyBindingOccurrenceCount: Int,
    val legacyPlaceholderCount: Int,
)
