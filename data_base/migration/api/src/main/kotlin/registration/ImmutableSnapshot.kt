package gog.my_project.data_base_migration.api.registration

import java.util.ArrayList
import java.util.Collections

internal fun <T> immutableSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
