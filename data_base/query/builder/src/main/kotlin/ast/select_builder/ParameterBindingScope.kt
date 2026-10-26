package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst

/** Canonicalizes equivalent logical parameters inside one independently built Query scope. */
internal class ParameterBindingScope {
    private val parameterNodesByName = mutableMapOf<String, MutableList<IQueryParameterAst<*>>>()
    private val reservedNames = mutableSetOf<String>()
    private var nextGeneratedNameIndex = 0

    /** Allocates deterministic names across both legacy and independent AST parameter paths. */
    fun nextParameterName(): String {
        while (true) {
            val candidate = "param${nextGeneratedNameIndex++}"
            if (reservedNames.add(candidate)) return candidate
        }
    }

    fun reserveParameterName(name: String?) {
        name?.let { reservedNames.add(it) }
    }

    /** Reuses equivalent definitions while keeping same-name values and SQL types distinct. */
    fun canonical(parameter: IQueryParameterAst<*>): IQueryParameterAst<*> {
        val name = requireNotNull(parameter.metadata.name) { "Parameter AST metadata name is missing" }
        val sqlType = requireNotNull(parameter.metadata.sqlType) { "Parameter AST metadata SQL type is missing" }
        reserveParameterName(name)

        val definitions = parameterNodesByName.getOrPut(name) { mutableListOf() }
        return definitions.firstOrNull { existing ->
            existing.metadata.value == parameter.metadata.value && existing.metadata.sqlType == sqlType
        } ?: parameter.also(definitions::add)
    }
}
