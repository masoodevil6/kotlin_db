package gog.my_project.data_base_migration.api.builder

import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_schema.SchemaOperation

class MigrationDefinitionBuilder {
    private var operation: SchemaOperation? = null

    /** Adds the single top-level Schema operation supplied by the public Schema facade. */
    fun operation(operation: SchemaOperation) {
        check(this.operation == null) {
            "MigrationDefinitionBuilder accepts exactly one top-level Schema operation"
        }
        this.operation = operation
    }

    internal fun build(): MigrationDefinition = MigrationDefinition(
        checkNotNull(operation) {
            "MigrationDefinitionBuilder requires one top-level Schema operation"
        },
    )
}

fun migration(block: MigrationDefinitionBuilder.() -> Unit): MigrationDefinition =
    MigrationDefinitionBuilder().apply(block).build()
