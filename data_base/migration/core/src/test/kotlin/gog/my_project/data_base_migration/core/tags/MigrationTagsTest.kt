package gog.my_project.data_base_migration.core.tags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private enum class CoreMigrationTagsVersions(override val value: String) : MigrationVersionTag {
    V1("v1"),
    V2("v2"),
    DUPLICATE("v1"),
}

private enum class CoreMigrationTagsGroups(override val value: String) : MigrationGroupTag {
    SCHEMA("schema"),
    DATA("data"),
    DUPLICATE("schema"),
}

private enum class CoreMigrationTagsItems(override val value: String) : MigrationItemTag {
    CREATE_CUSTOMERS("create_customers"),
    ADD_CUSTOMER_EMAIL("add_customer_email"),
    DUPLICATE("create_customers"),
}

class MigrationTagsTest {
    @Test
    fun `tag categories preserve their insertion order`() {
        val tags = MigrationTags(
            versionTags = listOf(CoreMigrationTagsVersions.V1, CoreMigrationTagsVersions.V2),
            groupTags = listOf(CoreMigrationTagsGroups.SCHEMA, CoreMigrationTagsGroups.DATA),
            itemTags = listOf(CoreMigrationTagsItems.CREATE_CUSTOMERS, CoreMigrationTagsItems.ADD_CUSTOMER_EMAIL),
        )

        assertEquals(listOf("v1", "v2"), tags.versionTags.map { it.value })
        assertEquals(listOf("schema", "data"), tags.groupTags.map { it.value })
        assertEquals(listOf("create_customers", "add_customer_email"), tags.itemTags.map { it.value })
    }

    @Test
    fun `same name is allowed in different tag categories`() {
        val sharedVersion = object : MigrationVersionTag { override val value = "shared" }
        val sharedGroup = object : MigrationGroupTag { override val value = "shared" }
        val sharedItem = object : MigrationItemTag { override val value = "shared" }
        val tags = MigrationTags(
            versionTags = listOf(sharedVersion),
            groupTags = listOf(sharedGroup),
            itemTags = listOf(sharedItem),
        )

        assertEquals("shared", tags.versionTags.single().value)
        assertEquals("shared", tags.groupTags.single().value)
        assertEquals("shared", tags.itemTags.single().value)
    }

    @Test
    fun `duplicates are rejected independently within every category`() {
        assertFailsWith<IllegalArgumentException> {
            MigrationTags(versionTags = listOf(CoreMigrationTagsVersions.V1, CoreMigrationTagsVersions.DUPLICATE))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationTags(groupTags = listOf(CoreMigrationTagsGroups.SCHEMA, CoreMigrationTagsGroups.DUPLICATE))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationTags(itemTags = listOf(CoreMigrationTagsItems.CREATE_CUSTOMERS, CoreMigrationTagsItems.DUPLICATE))
        }
    }

    @Test
    fun `inputs are copied and exposed as unmodifiable ordered snapshots`() {
        val versions = mutableListOf<MigrationVersionTag>(CoreMigrationTagsVersions.V1)
        val groups = mutableListOf<MigrationGroupTag>(CoreMigrationTagsGroups.SCHEMA)
        val items = mutableListOf<MigrationItemTag>(CoreMigrationTagsItems.CREATE_CUSTOMERS)
        val tags = MigrationTags(versions, groups, items)

        versions += CoreMigrationTagsVersions.V2
        groups += CoreMigrationTagsGroups.DATA
        items += CoreMigrationTagsItems.ADD_CUSTOMER_EMAIL

        assertEquals(listOf("v1"), tags.versionTags.map { it.value })
        assertEquals(listOf("schema"), tags.groupTags.map { it.value })
        assertEquals(listOf("create_customers"), tags.itemTags.map { it.value })

        assertFailsWith<UnsupportedOperationException> {
            (tags.versionTags as MutableList).add(CoreMigrationTagsVersions.V2)
        }
        assertFailsWith<UnsupportedOperationException> {
            (tags.groupTags as MutableList).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (tags.itemTags as MutableList).clear()
        }
    }
}
