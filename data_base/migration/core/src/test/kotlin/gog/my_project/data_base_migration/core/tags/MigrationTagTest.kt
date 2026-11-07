package gog.my_project.data_base_migration.core.tags

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class CoreMigrationTagVersions(override val value: String) : MigrationVersionTag {
    RELEASE_1("release_1"),
    BLANK(" \t "),
    TOO_LONG("v".repeat(256)),
}

private enum class CoreMigrationTagGroups(override val value: String) : MigrationGroupTag {
    BACKEND("backend"),
    BLANK("  \n"),
}

private enum class CoreMigrationTagItems(override val value: String) : MigrationItemTag {
    CREATE_CUSTOMERS("create_customers"),
    INVALID("Create Customers"),
}

class MigrationTagTest {
    @Test
    fun `tag categories retain stable types and type codes`() {
        assertEquals(0, MigrationTagType.VERSION.code)
        assertEquals(1, MigrationTagType.ITEM.code)
        assertEquals(2, MigrationTagType.GROUP.code)
        assertTrue(MigrationTagType.VERSION.isPersisted)
        assertTrue(MigrationTagType.ITEM.isPersisted)
        assertFalse(MigrationTagType.GROUP.isPersisted)

        assertEquals(MigrationTagType.VERSION, CoreMigrationTagVersions.RELEASE_1.type)
        assertEquals(MigrationTagType.GROUP, CoreMigrationTagGroups.BACKEND.type)
        assertEquals(MigrationTagType.ITEM, CoreMigrationTagItems.CREATE_CUSTOMERS.type)
    }

    @Test
    fun `enum values provide the public tag names and preserve validation rules`() {
        assertEquals("release_1", CoreMigrationTagVersions.RELEASE_1.validatedValue)
        assertEquals("backend", CoreMigrationTagGroups.BACKEND.validatedValue)
        assertEquals("create_customers", CoreMigrationTagItems.CREATE_CUSTOMERS.value)

        assertFailsWith<IllegalArgumentException> { CoreMigrationTagVersions.BLANK.validatedValue }
        assertFailsWith<IllegalArgumentException> { CoreMigrationTagVersions.TOO_LONG.validatedValue }
        assertFailsWith<IllegalArgumentException> { CoreMigrationTagGroups.BLANK.validatedValue }
        assertFailsWith<IllegalArgumentException> {
            MigrationIdentity.fromValue(CoreMigrationTagItems.INVALID.value)
        }
    }

    @Test
    fun `item tag and MigrationIdentity share one immutable validated identity`() {
        val identity = MigrationIdentity.fromValue("create_customers")

        assertEquals("create_customers", identity.value)
        assertEquals(identity, MigrationIdentity.fromValue(CoreMigrationTagItems.CREATE_CUSTOMERS.value))
        assertFailsWith<IllegalArgumentException> {
            MigrationIdentity.fromValue("Create Customers")
        }
    }
}
