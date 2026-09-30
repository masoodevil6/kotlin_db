package gog.my_project.data_base.migration.example.v1.migrations.create_unique_index

import gog.my_project.data_base.migration.ast.schema.create_unique_index.MigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.builder.createUniqueIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleCreateUniqueIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersSingleColumnUniqueIndex() {
        assertEquals(
            "CREATE UNIQUE INDEX `uq_users_email` ON `users` (`email`)",
            dialect.render(A1ExampleCreateUniqueIndexV1().migration().ast),
        )
    }

    @Test
    fun trimsWhitespaceAndPreservesIdentifierCasing() {
        val migration = createUniqueIndex {
            tableName("  Users  ")
            name("  Uq_User_Email  ")
            column("  Email  ")
        }

        assertEquals("Users", migration.ast.tableName)
        assertEquals("Uq_User_Email", migration.ast.indexName)
        assertEquals("Email", migration.ast.columnName)
        assertEquals(
            "CREATE UNIQUE INDEX `Uq_User_Email` ON `Users` (`Email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun primaryIsNotSpecialCasedAndIsQuotedLikeAnyIndexName() {
        val migration = createUniqueIndex {
            tableName("users")
            name("PRIMARY")
            column("email")
        }

        assertEquals("PRIMARY", migration.ast.indexName)
        assertEquals(0, migration.params.size)
        assertEquals(
            "CREATE UNIQUE INDEX `PRIMARY` ON `users` (`email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rejectsBlankBuilderValuesAndIncompleteDirectAst() {
        assertFailsWith<IllegalArgumentException> {
            createUniqueIndex { tableName(" "); name("uq"); column("email") }
        }
        assertFailsWith<IllegalArgumentException> {
            createUniqueIndex { tableName("users"); name(" "); column("email") }
        }
        assertFailsWith<IllegalArgumentException> {
            createUniqueIndex { tableName("users"); name("uq"); column(" ") }
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateUniqueIndexAst().apply {
                tableName = "users"
                indexName = "uq_users_email"
            })
        }
    }

    @Test
    fun quotesReservedIdentifiersEscapesBackticksAndAddsNoOptions() {
        val sql = createUniqueIndex {
            tableName("order")
            name("select`email")
            column("user`email")
        }.let { dialect.render(it.ast)!! }

        assertEquals("CREATE UNIQUE INDEX `select``email` ON `order` (`user``email`)", sql)
        assertEquals(1, "UNIQUE".toRegex().findAll(sql).count())
        kotlin.test.assertFalse(sql.contains("IF NOT EXISTS"))
        kotlin.test.assertFalse(sql.startsWith("ALTER TABLE"))
    }

    @Test
    fun setterCallOrderDoesNotAffectSql() {
        val first = createUniqueIndex { column("email"); name("uq_users_email"); tableName("users") }
        val second = createUniqueIndex { tableName("users"); name("uq_users_email"); column("email") }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }
}
