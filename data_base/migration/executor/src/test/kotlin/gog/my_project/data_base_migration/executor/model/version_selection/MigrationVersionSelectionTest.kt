package gog.my_project.data_base_migration.executor.model.version_selection

import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MigrationVersionSelectionTest {
    @Test
    fun retainsUpDirection() {
        val selection = MigrationVersionSelection(MigrationVersionSelectionDirection.UP, emptyList())

        assertEquals(MigrationVersionSelectionDirection.UP, selection.direction)
    }

    @Test
    fun retainsDownDirection() {
        val selection = MigrationVersionSelection(MigrationVersionSelectionDirection.DOWN, emptyList())

        assertEquals(MigrationVersionSelectionDirection.DOWN, selection.direction)
    }

    @Test
    fun preservesInputOrderAndVersionIdentity() {
        val first = preparedVersion("first")
        val second = preparedVersion("second")
        val selection = MigrationVersionSelection(MigrationVersionSelectionDirection.UP, listOf(first, second))

        assertEquals(listOf("first", "second"), selection.versions.map { it.version.name })
        assertSame(first, selection.versions[0])
        assertSame(second, selection.versions[1])
    }

    @Test
    fun copiesInputListDefensively() {
        val first = preparedVersion("first")
        val second = preparedVersion("second")
        val source = mutableListOf(first)
        val selection = MigrationVersionSelection(MigrationVersionSelectionDirection.UP, source)

        source.clear()
        source += second

        assertEquals(listOf(first), selection.versions)
    }

    @Test
    fun exposesUnmodifiableVersionsList() {
        val selection = MigrationVersionSelection(
            MigrationVersionSelectionDirection.DOWN,
            listOf(preparedVersion("first")),
        )

        assertFailsWith<UnsupportedOperationException> {
            (selection.versions as MutableList).clear()
        }
    }

    @Test
    fun supportsEmptySelection() {
        val selection = MigrationVersionSelection(MigrationVersionSelectionDirection.UP, emptyList())

        assertEquals(emptyList(), selection.versions)
    }

    private fun preparedVersion(name: String) =
        model.prepared.PreparedMigrationVersion(MigrationVersion(name, emptyList()), emptyList())
}
