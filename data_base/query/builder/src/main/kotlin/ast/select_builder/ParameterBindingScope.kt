package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.query.reader.SqlParameter

/** Canonicalizes equivalent logical parameters inside one independently built Query scope. */
internal class ParameterBindingScope {
    private val parametersByName = mutableMapOf<String, SqlParameter<*>>()

    fun canonical(parameter: SqlParameter<*>): SqlParameter<*> {
        val existing = parametersByName[parameter.name]
        if (existing == null) {
            parametersByName[parameter.name] = parameter
            return parameter
        }

        require(existing.value == parameter.value && existing.sqlType == parameter.sqlType) {
            "Conflicting values supplied for parameter '${parameter.name}'"
        }
        return existing
    }
}
