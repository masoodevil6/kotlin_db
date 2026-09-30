package gog.my_project.data_base.migration.api.interfaces

/** A validated migration identity. Its value is kept exactly as declared in [MigrationId]. */
@JvmInline
value class MigrationIdentity private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 255
        private val GRAMMAR = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")

        /** Validates an annotation value without trimming or rewriting it. */
        fun fromAnnotationValue(value: String): MigrationIdentity {
            require(value.isNotBlank()) { "MigrationId must not be blank" }
            require(value.length <= MAX_LENGTH) {
                "MigrationId must not exceed $MAX_LENGTH characters"
            }
            require(GRAMMAR.matches(value)) {
                "MigrationId '$value' must match ${GRAMMAR.pattern}"
            }
            return MigrationIdentity(value)
        }
    }
}
