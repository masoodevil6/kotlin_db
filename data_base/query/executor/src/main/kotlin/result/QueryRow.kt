package gog.my_project.data_base.query.executer.result

/** An eagerly materialized SELECT row, keyed only by SQL output alias. */
class QueryRow internal constructor(
    values: List<Pair<String, Any?>>,
) {
    private val values = values.toList()

    init {
        require(this.values.all { it.first.isNotBlank() }) {
            "QueryRow keys must be non-empty SQL output aliases"
        }
        require(this.values.map { it.first }.distinct().size == this.values.size) {
            "QueryRow SQL output aliases must be unique"
        }
    }

    operator fun get(alias: String): Any? {
        require(alias.isNotBlank()) { "QueryRow alias must not be blank" }
        val entry = values.firstOrNull { it.first == alias }
            ?: throw IllegalArgumentException("Unknown SELECT output alias '$alias'")
        return entry.second
    }
}
