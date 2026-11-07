package gog.my_project.data_base_migration.core.tags

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import java.util.Collections

/** Immutable, ordered snapshots of the distinct migration tag categories. */
class MigrationTags(
    versionTags: List<MigrationVersionTag> = emptyList(),
    groupTags: List<MigrationGroupTag> = emptyList(),
    itemTags: List<MigrationItemTag> = emptyList(),
) {
    val versionTags: List<MigrationVersionTag> = immutableSnapshot(versionTags)
    val groupTags: List<MigrationGroupTag> = immutableSnapshot(groupTags)
    val itemTags: List<MigrationItemTag> = immutableSnapshot(itemTags)

    init {
        requireUnique(this.versionTags.map(MigrationVersionTag::validatedValue), "version")
        requireUnique(this.groupTags.map(MigrationGroupTag::validatedValue), "group")
        requireUnique(this.itemTags.map { MigrationIdentity.fromValue(it.value).value }, "migration ID")
    }

    private fun requireUnique(names: List<String>, category: String) {
        require(names.size == names.toSet().size) { "Duplicate $category tag" }
    }

    private fun <T> immutableSnapshot(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))
}
