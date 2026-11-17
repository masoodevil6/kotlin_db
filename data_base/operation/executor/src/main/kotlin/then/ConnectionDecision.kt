package gog.my_project.data_base_operation.then

/** Selects the Operation connection that the next Pipeline stage should use. */
enum class ConnectionDecision {
    /** Continue on the current Operation Scope and its Connection. */
    REUSE,

    /** Request a fresh Operation Scope and Connection. Not supported in Pipeline Phase 1. */
    NEW,
}
