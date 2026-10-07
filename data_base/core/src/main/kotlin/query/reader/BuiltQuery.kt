package gog.my_project.data_base.core.query.reader

private val namedParameterPattern = Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")

data class BuiltQuery(
    val query: String?,
    val params: MutableList<SqlParameter<*>>,
) {
    /** Returns every named-placeholder occurrence in SQL text order and validates its parameters. */
    fun getListParamNames(): List<String> {
        val sql = query ?: return emptyList()
        val occurrences = namedParameterPattern.findAll(sql)
            .map { match -> match.groupValues[1] }
            .toList()
        val occurrenceNames = occurrences.toHashSet()
        val parametersByName = linkedMapOf<String, SqlParameter<*>>()

        for (parameter in params) {
            val existing = parametersByName[parameter.name]
            if (existing == null) {
                parametersByName[parameter.name] = parameter
            } else {
                require(existing.value == parameter.value) {
                    "Conflicting values supplied for parameter '${parameter.name}'"
                }
                require(existing.sqlType == parameter.sqlType) {
                    "Conflicting SQL types supplied for parameter '${parameter.name}'"
                }
            }
        }

        for (name in occurrences) {
            require(parametersByName.containsKey(name)) {
                "Placeholder ':$name' has no matching parameter"
            }
        }
        for (parameter in params) {
            require(parameter.name in occurrenceNames) {
                "Parameter '${parameter.name}' has no matching placeholder"
            }
        }

        return occurrences
    }

    /** Replaces each recognized placeholder in one left-to-right pass. */
    fun getReadyQuery(): String? = query?.let { sql ->
        namedParameterPattern.replace(sql) { "?" }
    }
}
