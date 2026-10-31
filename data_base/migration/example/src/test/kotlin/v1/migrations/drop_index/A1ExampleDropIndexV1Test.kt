package gog.my_project.data_base.migration.example.v1.migrations.drop_index

import gog.my_project.data_base.migration.ast.schema.drop_index.MigrationDropIndexAst
import gog.my_project.data_base.migration.builder.ast.drop_index.MigrationDropIndexBuilder
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleDropIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersDropIndexWithMySqlSyntax() {
        assertEquals(
            "DROP INDEX `idx_users_email` ON `users`",
            dialect.render(A1ExampleDropIndexV1().migration().ast),
        )
    }

    @Test
    fun trimsWhitespaceAndPreservesIdentifierCasing() {
        val migration = MigrationDropIndexBuilder().apply {
            tableName("  Users  ")
            name("  Idx_User_Email  ")
        }

        assertEquals("Users", migration.ast.metadata.tableName)
        assertEquals("Idx_User_Email", migration.ast.metadata.indexName)
        assertEquals("DROP INDEX `Idx_User_Email` ON `Users`", dialect.render(migration.ast))
    }

    @Test
    fun primaryIsAnOrdinaryIndexNameAndIsQuoted() {
        val migration = MigrationDropIndexBuilder().apply {
            tableName("users")
            name("PRIMARY")
        }

        assertEquals("PRIMARY", migration.ast.metadata.indexName)
        assertEquals("DROP INDEX `PRIMARY` ON `users`", dialect.render(migration.ast))
    }

    @Test
    fun rendersBlankIdentifiersAndRejectsIncompleteDirectAst() {
        assertEquals("DROP INDEX `idx` ON ``", dialect.render(MigrationDropIndexBuilder().apply { tableName(" "); name("idx") }.ast))
        assertEquals("DROP INDEX `` ON `users`", dialect.render(MigrationDropIndexBuilder().apply { tableName("users"); name(" ") }.ast))
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationDropIndexAst().apply { metadata.tableName = "users" })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationDropIndexAst().apply { metadata.indexName = "idx_users_email" })
        }
    }

    @Test
    fun quotesReservedWordsAndEscapesBackticksWithoutAddingOptions() {
        val sql = MigrationDropIndexBuilder().apply {
            tableName("order")
            name("select`index")
        }.let { dialect.render(it.ast)!! }

        assertEquals("DROP INDEX `select``index` ON `order`", sql)
        kotlin.test.assertFalse(sql.contains("IF EXISTS"))
        kotlin.test.assertFalse(sql.startsWith("ALTER TABLE"))
    }

    @Test
    fun setterCallOrderDoesNotAffectSql() {
        val first = MigrationDropIndexBuilder().apply { name("idx_users_email"); tableName("users") }
        val second = MigrationDropIndexBuilder().apply { tableName("users"); name("idx_users_email") }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }
}
