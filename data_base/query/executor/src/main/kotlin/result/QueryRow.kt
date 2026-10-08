package gog.my_project.data_base.query.executer.result

import kotlin.reflect.KProperty1

/** An eagerly materialized SELECT row, keyed only by SQL output alias. */
class QueryRow internal constructor(
    values: List<Pair<String, Any?>>,
    private val propertyAliases: List<Pair<KProperty1<*, *>, String>> = emptyList(),
) {

    private val values: LinkedHashMap<String, Any?> = linkedMapOf<String, Any?>().apply {
        values.forEach { (alias, value) ->
            require(alias.isNotBlank()) {
                "QueryRow keys must be non-empty SQL output aliases"
            }

            require(alias !in this) {
                "QueryRow SQL output aliases must be unique"
            }

            this[alias] = value
        }
    }

    operator fun set(alias: String, value: Any?) {
        require(alias.isNotBlank()) {
            "QueryRow alias must not be blank"
        }

        values[alias] = value
    }


    operator fun get(alias: String): Any? {
        require(alias.isNotBlank()) {
            "QueryRow alias must not be blank"
        }

        require(values.containsKey(alias)) {
            "Unknown SELECT output alias '$alias'"
        }
        return values[alias]
    }

    fun getValue(outputKey: String): Any? = get(outputKey)

    fun <T, R> getValue(
        property: KProperty1<T, R>,
        outputKeyHint: String? = null,
    ): Any? {
        if (outputKeyHint != null) {
            require(outputKeyHint.isNotBlank()) {
                "Output key hint for property '${property.name}' must not be blank"
            }
        }

        val matchingAliases = propertyAliases
            .filter { (selectedProperty, _) -> selectedProperty == property }
            .map { (_, alias) -> alias }

        require(matchingAliases.isNotEmpty()) {
            "Property '${property.name}' is not selected in this QueryRow"
        }

        val resolvedAlias = if (outputKeyHint != null) {
            require(outputKeyHint in matchingAliases) {
                "Output key hint '$outputKeyHint' does not identify a selected output for property '${property.name}'"
            }
            outputKeyHint
        } else {
            require(matchingAliases.size == 1) {
                "Property '${property.name}' is selected more than once; provide an output key hint"
            }
            matchingAliases.single()
        }

        return get(resolvedAlias)
    }
}
