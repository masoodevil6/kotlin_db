package gog.my_project.data_base.migration.example.v1.migrations.drop_index

import gog.my_project.data_base.migration.ast.schema.drop_index.MigrationDropIndexAst
import gog.my_project.data_base.migration.builder.dropIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        val migration = dropIndex {
            tableName("  Users  ")
            name("  Idx_User_Email  ")
        }

        assertEquals("Users", migration.ast.tableName)
        assertEquals("Idx_User_Email", migration.ast.indexName)
        assertEquals("DROP INDEX `Idx_User_Email` ON `Users`", dialect.render(migration.ast))
    }

    @Test
    fun primaryIsAnOrdinaryIndexNameAndIsQuoted() {
        val migration = dropIndex {
            tableName("users")
            name("PRIMARY")
        }

        assertEquals("PRIMARY", migration.ast.indexName)
        assertEquals("DROP INDEX `PRIMARY` ON `users`", dialect.render(migration.ast))
    }

    @Test
    fun rejectsBlankIdentifiersInBuilderAndDirectAstInRenderer() {
        assertFailsWith<IllegalArgumentException> { dropIndex { tableName(" "); name("idx") } }
        assertFailsWith<IllegalArgumentException> { dropIndex { tableName("users"); name(" ") } }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationDropIndexAst().apply { tableName = "users" })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationDropIndexAst().apply { indexName = "idx_users_email" })
        }
    }

    @Test
    fun quotesReservedWordsAndEscapesBackticksWithoutAddingOptions() {
        val sql = dropIndex {
            tableName("order")
            name("select`index")
        }.let { dialect.render(it.ast)!! }

        assertEquals("DROP INDEX `select``index` ON `order`", sql)
        kotlin.test.assertFalse(sql.contains("IF EXISTS"))
        kotlin.test.assertFalse(sql.startsWith("ALTER TABLE"))
    }

    @Test
    fun setterCallOrderDoesNotAffectSql() {
        val first = dropIndex { name("idx_users_email"); tableName("users") }
        val second = dropIndex { tableName("users"); name("idx_users_email") }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }
}
