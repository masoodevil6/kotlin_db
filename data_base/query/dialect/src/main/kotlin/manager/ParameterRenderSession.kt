package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import java.util.IdentityHashMap

internal enum class ParameterPlaceholderMode {
    Named,
    Positional,
}

/** Per-pass state. It is created for one render call and shared only by recursive dispatches. */
internal class ParameterRenderSession(
    val placeholderMode: ParameterPlaceholderMode,
    val requireOccurrenceAwareRenderers: Boolean,
    private val generatedBindings: IdentityHashMap<IQueryParameterAst<*>, IQueryParameterBinding> =
        IdentityHashMap(),
) {
    private enum class EventKind { AstBinding, LegacyBinding, LegacyPlaceholder }

    private val events = mutableListOf<EventKind>()
    val occurrences: MutableList<IQueryParameterBinding> = mutableListOf()
    var emittedParameterCount: Int = 0
        private set
    var astParameterOccurrenceCount: Int = 0
        private set
    var legacyBindingOccurrenceCount: Int = 0
        private set
    var legacyPlaceholderCount: Int = 0
        private set

    val eventCount: Int
        get() = events.size

    fun parameterFor(node: IQueryParameterAst<*>): IQueryParameterBinding {
        val name = requireNotNull(node.metadata.name) { "Parameter AST metadata name is missing" }
        val sqlType = requireNotNull(node.metadata.sqlType) { "Parameter AST metadata SQL type is missing" }
        return generatedBindings.getOrPut(node) {
            QueryParameterBinding(name = name, value = node.metadata.value, sqlType = sqlType)
        }
    }

    fun recordAst(parameter: IQueryParameterBinding) {
        record(parameter, EventKind.AstBinding)
        astParameterOccurrenceCount++
    }

    fun recordLegacyBinding(parameter: IQueryParameterBinding) {
        record(parameter, EventKind.LegacyBinding)
        legacyBindingOccurrenceCount++
    }

    fun recordLegacyPlaceholder() {
        events += EventKind.LegacyPlaceholder
        legacyPlaceholderCount++
    }

    private fun record(parameter: IQueryParameterBinding, kind: EventKind) {
        occurrences += parameter
        events += kind
        emittedParameterCount++
    }

    fun validatedOccurrences(passName: String): List<IQueryParameterBinding> {
        check(emittedParameterCount == occurrences.size) {
            "$passName renderer emission count $emittedParameterCount does not match " +
                "recorded occurrence count ${occurrences.size}"
        }
        return occurrences.toList()
    }

    fun rollbackToEvent(startIndex: Int) {
        require(startIndex in 0..events.size) {
            "Invalid parameter render rollback index: $startIndex"
        }
        while (events.size > startIndex) {
            when (events.removeAt(events.lastIndex)) {
                EventKind.AstBinding -> {
                    occurrences.removeAt(occurrences.lastIndex)
                    emittedParameterCount--
                    astParameterOccurrenceCount--
                }
                EventKind.LegacyBinding -> {
                    occurrences.removeAt(occurrences.lastIndex)
                    emittedParameterCount--
                    legacyBindingOccurrenceCount--
                }
                EventKind.LegacyPlaceholder -> legacyPlaceholderCount--
            }
        }
    }

    fun rollbackToOccurrenceCount(startIndex: Int) {
        require(startIndex in 0..occurrences.size) {
            "Invalid parameter occurrence rollback index: $startIndex"
        }
        val eventIndex = if (startIndex == occurrences.size) {
            events.size
        } else {
            var seenBindings = 0
            events.indexOfFirst { kind ->
                val isBinding = kind == EventKind.AstBinding || kind == EventKind.LegacyBinding
                if (isBinding && seenBindings++ == startIndex) true else false
            }
        }
        rollbackToEvent(eventIndex)
    }
}
