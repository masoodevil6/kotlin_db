package gog.my_project.data_base.query.renderer.manager

import gog.my_project.data_base.core.query.reader.SqlParameter

internal enum class ParameterPlaceholderMode {
    Named,
    Positional,
}

/** Per-pass state. It is created for one render call and shared only by recursive dispatches. */
internal class ParameterRenderSession(
    val placeholderMode: ParameterPlaceholderMode,
    val requireOccurrenceAwareRenderers: Boolean,
) {
    val occurrences: MutableList<Any> = mutableListOf()
    var emittedParameterCount: Int = 0
        private set

    fun record(parameter: SqlParameter<*>) {
        occurrences += parameter
        emittedParameterCount++
    }

    fun rollbackTo(startIndex: Int) {
        require(startIndex in 0..occurrences.size) {
            "Invalid parameter occurrence rollback index: $startIndex"
        }
        val removed = occurrences.size - startIndex
        repeat(removed) { occurrences.removeAt(occurrences.lastIndex) }
        emittedParameterCount = (emittedParameterCount - removed).coerceAtLeast(0)
    }
}
