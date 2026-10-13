package gog.my_project.data_base.query.meta_data.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import java.util.Collections
import java.util.IdentityHashMap

/** Describes how parameter-bearing values are represented in a query AST. */
enum class QueryParameterRepresentation {
    IdentityAware,
    LegacyOnly,
    MixedOrUnsupported,
}

/**
 * Parameter definitions discovered in a query AST.
 *
 * Definitions are held by reference identity. The ordered occurrence list is supplied by the
 * renderer later; validation returns an immutable snapshot in exactly the order it was supplied.
 */
class QueryParameterMetadata internal constructor(
    val representation: QueryParameterRepresentation,
    definitions: Collection<SqlParameter<*>>,
) {
    private val definitionsByIdentity = IdentityHashMap<SqlParameter<*>, Boolean>().apply {
        definitions.forEach { this[it] = true }
    }

    /** Returns true only for the exact parameter object discovered in the AST. */
    fun containsDefinition(parameter: SqlParameter<*>): Boolean =
        definitionsByIdentity.containsKey(parameter)

    /**
     * Validates that every rendered occurrence is one of this AST's definitions.
     * Repeated references are valid and their order is preserved in the returned snapshot.
     * AST definitions that do not occur in the rendered output are allowed.
     */
    fun validateOccurrences(
        occurrences: List<SqlParameter<*>>,
    ): List<SqlParameter<*>> {
        occurrences.forEachIndexed { index, parameter ->
            require(containsDefinition(parameter)) {
                "Parameter occurrence at index $index is not defined in the inspected query AST"
            }
        }

        return Collections.unmodifiableList(ArrayList(occurrences))
    }

    /** Validates renderer emission count independently from AST-definition membership. */
    fun validateOccurrenceCount(
        emittedParameterCount: Int,
        occurrences: List<SqlParameter<*>>,
    ) {
        require(emittedParameterCount >= 0) {
            "Emitted parameter count must not be negative: $emittedParameterCount"
        }
        require(emittedParameterCount == occurrences.size) {
            "Parameter emission count $emittedParameterCount does not match " +
                "occurrence count ${occurrences.size}"
        }
    }
}
