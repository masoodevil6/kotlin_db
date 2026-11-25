package gog.my_project.data_base_session.dialect.manager

import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter

/** Ordered parameter occurrences collected for one command render. */
class SessionRenderState {
    private val owner = Any()
    private val occurrences = mutableListOf<SessionCommandParameter>()

    class Checkpoint internal constructor(
        internal val owner: Any,
        internal val occurrenceCount: Int,
    )

    fun emit(parameter: SessionCommandParameter) {
        occurrences += parameter
    }

    fun checkpoint(): Checkpoint = Checkpoint(owner, occurrences.size)

    fun rollbackTo(checkpoint: Checkpoint) {
        require(checkpoint.owner === owner) { "Checkpoint belongs to a different Session render state." }
        require(checkpoint.occurrenceCount in 0..occurrences.size) { "Checkpoint is no longer valid." }

        while (occurrences.size > checkpoint.occurrenceCount) {
            occurrences.removeAt(occurrences.lastIndex)
        }
    }

    fun snapshot(): List<SessionCommandParameter> = occurrences.toList()
}
