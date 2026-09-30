package gog.my_project.data_base.migration.api.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationIdentityTest {
    @Test
    fun preservesValidIdentityExactly() {
        val identity = MigrationIdentity.fromAnnotationValue("create_users2_email")

        assertEquals("create_users2_email", identity.value)
    }

    @Test
    fun acceptsMaximumLength() {
        val value = "a" + "b".repeat(254)

        assertEquals(value, MigrationIdentity.fromAnnotationValue(value).value)
    }

    @Test
    fun rejectsMoreThanMaximumLength() {
        val value = "a" + "b".repeat(255)

        assertFailsWith<IllegalArgumentException> {
            MigrationIdentity.fromAnnotationValue(value)
        }
    }

    @Test
    fun rejectsBlankAndInvalidGrammarWithoutNormalization() {
        listOf(
            "",
            "   ",
            "CreateUsers",
            "_create_users",
            "create__users",
            "create_users_",
            "2_create_users",
            "create users",
            "créate_users",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                MigrationIdentity.fromAnnotationValue(value)
            }
        }
    }
}
