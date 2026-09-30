package gog.my_project.data_base.migration.api.interfaces

import java.util.ArrayList
import java.util.Collections

class MigrationDefinition(operations: List<IMigrationApi<*>>) {
    val operations: List<IMigrationApi<*>> =
        Collections.unmodifiableList(ArrayList(operations))
}
