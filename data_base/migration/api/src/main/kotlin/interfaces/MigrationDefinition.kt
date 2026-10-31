package gog.my_project.data_base.migration.api.interfaces

import java.util.ArrayList
import java.util.Collections

class MigrationDefinition(operations: List<IMigrationApi<*>>) {
    val operations: List<IMigrationApi<*>> =
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
