package gog.my_project.data_base.migration.example.v1.migrations.create_unique_index

import gog.my_project.data_base_schema.SchemaDialect

import gog.my_project.data_base_schema.Schema


import gog.my_project.data_base_schema.ast.schema.create_unique_index.SchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.builder.ast.create_unique_index.SchemaCreateUniqueIndexBuilder
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class A1ExampleCreateUniqueIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersSingleColumnUniqueIndex() {
        assertEquals(
            "CREATE UNIQUE INDEX `uq_users_email` ON `users` (`email`)",
            Schema.render(A1ExampleCreateUniqueIndexV1().migration(), SchemaDialect.MYSQL),
        )
    }

    @Test
    fun trimsWhitespaceAndPreservesIdentifierCasing() {
        val migration = SchemaCreateUniqueIndexBuilder().apply {
            tableName("  Users  ")
            name("  Uq_User_Email  ")
            column("  Email  ")
        }

        assertEquals("Users", migration.ast.metadata.tableName)
        assertEquals("Uq_User_Email", migration.ast.metadata.indexName)
        assertEquals("Email", migration.ast.metadata.columnName)
        assertEquals(
            "CREATE UNIQUE INDEX `Uq_User_Email` ON `Users` (`Email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun primaryIsNotSpecialCasedAndIsQuotedLikeAnyIndexName() {
        val migration = SchemaCreateUniqueIndexBuilder().apply {
            tableName("users")
            name("PRIMARY")
            column("email")
        }

        assertEquals("PRIMARY", migration.ast.metadata.indexName)
        assertEquals(
            "CREATE UNIQUE INDEX `PRIMARY` ON `users` (`email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rendersBlankValuesAndRejectsMissingDirectAstColumn() {
        assertEquals(
            "CREATE UNIQUE INDEX `uq` ON `` (`email`)",
            dialect.render(SchemaCreateUniqueIndexBuilder().apply { tableName(" "); name("uq"); column("email") }.ast),
        )
        assertEquals(
            "CREATE UNIQUE INDEX `` ON `users` (`email`)",
            dialect.render(SchemaCreateUniqueIndexBuilder().apply { tableName("users"); name(" "); column("email") }.ast),
        )
        assertEquals(
            "CREATE UNIQUE INDEX `uq` ON `users` (``)",
            dialect.render(SchemaCreateUniqueIndexBuilder().apply { tableName("users"); name("uq"); column(" ") }.ast),
        )
        assertFailsWith<IllegalArgumentException> {
            dialect.render(SchemaCreateUniqueIndexAst().apply {
                metadata.tableName = "users"
                metadata.indexName = "uq_users_email"
            })
        }
    }

    @Test
    fun quotesReservedIdentifiersEscapesBackticksAndAddsNoOptions() {
        val sql = SchemaCreateUniqueIndexBuilder().apply {
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
        val first = SchemaCreateUniqueIndexBuilder().apply { column("email"); name("uq_users_email"); tableName("users") }
        val second = SchemaCreateUniqueIndexBuilder().apply { tableName("users"); name("uq_users_email"); column("email") }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }
}
