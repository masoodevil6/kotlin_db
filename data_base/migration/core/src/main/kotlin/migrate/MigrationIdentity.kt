package gog.my_project.data_base_migration.core.migrate

/** A validated, immutable migration item identity. */
@JvmInline
value class MigrationIdentity private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 255
        private val GRAMMAR = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")

        /** Validates a persisted identity without trimming or rewriting it. */
        fun fromValue(value: String): MigrationIdentity {
            require(value.isNotBlank()) { "Migration identity must not be blank" }
            require(value.length <= MAX_LENGTH) {
                "Migration identity must not exceed $MAX_LENGTH characters"
            }
            require(GRAMMAR.matches(value)) {
                "Migration identity '$value' must match ${GRAMMAR.pattern}"
            }
            return MigrationIdentity(value)
        }

        /** Compatibility alias; this function validates a value and does not inspect annotations. */
        @Deprecated("Use fromValue; migration identity is no longer resolved from annotations")
        fun fromAnnotationValue(value: String): MigrationIdentity = fromValue(value)
    }
}
