package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi

import java.util.ArrayList
import java.util.Collections

class MigrationDefinition(operations: List<ISchemaApi<*>>) {
    val operations: List<ISchemaApi<*>> =
        Collections.unmodifiableList(ArrayList(operations))

    init {
        require(this.operations.size == 1) {
            "MigrationDefinition must contain exactly one top-level operation; found ${this.operations.size}"
        }
        requireNotNull(this.operations[0]) {
            "MigrationDefinition must contain a non-null top-level operation"
        }
    }
}
