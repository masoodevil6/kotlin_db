package gog.my_project.data_base.migration.example.v1.migrations.create_index

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.schema.create_index.MigrationCreateIndexAst
import gog.my_project.data_base.migration.builder.ast.create_index.MigrationCreateIndexBuilder
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.Test




class A1ExampleCreateIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersSingleColumnNonUniqueIndex() {
        assertEquals(
            "CREATE INDEX `idx_users_email` ON `users` (`email`)",
            dialect.render(A1ExampleCreateIndexV1().migration().ast),
        )
    }

    @Test
    fun trimsSurroundingWhitespaceAndPreservesRemainingIdentifier() {
        val migration = MigrationCreateIndexBuilder().apply {
            tableName("  Users  ")
            name("  Idx_User_Email  ")
            column("  Email  ")
        }

        assertEquals("Users", migration.ast.metadata.tableName)
        assertEquals("Idx_User_Email", migration.ast.metadata.indexName)
        assertEquals("Email", migration.ast.metadata.columnName)
        assertEquals(
            "CREATE INDEX `Idx_User_Email` ON `Users` (`Email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rendersBlankIdentifiersAndRejectsMissingAstFields() {
        assertEquals(
            "CREATE INDEX `idx` ON `` (`email`)",
            dialect.render(MigrationCreateIndexBuilder().apply { tableName(" "); name("idx"); column("email") }.ast),
        )
        assertEquals(
            "CREATE INDEX `` ON `users` (`email`)",
            dialect.render(MigrationCreateIndexBuilder().apply { tableName("users"); name(" "); column("email") }.ast),
        )
        assertEquals(
            "CREATE INDEX `idx` ON `users` (``)",
            dialect.render(MigrationCreateIndexBuilder().apply { tableName("users"); name("idx"); column(" ") }.ast),
        )

        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateIndexAst().apply {
                metadata.tableName = "users"
                metadata.indexName = "idx_users_email"
            })
        }
    }

    @Test
    fun escapesAllIdentifierPositionsAndDoesNotAddUnique() {
        val migration = MigrationCreateIndexBuilder().apply {
            tableName("order")
            name("select")
            column("user`email")
        }
        val sql = dialect.render(migration.ast)!!
        assertEquals("CREATE INDEX `select` ON `order` (`user``email`)", sql)
        assertFalse(sql.contains("UNIQUE"))
    }

    @Test
    fun setterCallOrderDoesNotAffectSql() {
        val first = MigrationCreateIndexBuilder().apply {
            column("email"); name("idx_users_email"); tableName("users")
        }
        val second = MigrationCreateIndexBuilder().apply {
            tableName("users"); name("idx_users_email"); column("email")
        }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }

    @Test
    fun rendersOptionalBtreeAndHashMethods() {
        val btree = MigrationCreateIndexBuilder().apply {
            tableName("lookup")
            name("idx_lookup_code")
            column("code")
            using(IndexMethod.BTREE)
        }
        val hash = MigrationCreateIndexBuilder().apply {
            tableName("lookup")
            name("idx_lookup_code")
            column("code")
            using(IndexMethod.HASH)
        }

        assertEquals(
            "CREATE INDEX `idx_lookup_code` ON `lookup` (`code`) USING BTREE",
            dialect.render(btree.ast),
        )
        assertEquals(
            "CREATE INDEX `idx_lookup_code` ON `lookup` (`code`) USING HASH",
            dialect.render(hash.ast),
        )
        assertEquals(
            dialect.render(hash.ast),
            MariaDbDialect().render(hash.ast),
        )
    }

    @Test
    fun indexMethodDoesNotDependOnDslCallOrder() {
        val methodFirst = MigrationCreateIndexBuilder().apply {
            using(IndexMethod.HASH)
            column("code")
            name("idx_lookup_code")
            tableName("lookup")
        }
        val methodLast = MigrationCreateIndexBuilder().apply {
            tableName("lookup")
            name("idx_lookup_code")
            column("code")
            using(IndexMethod.HASH)
        }

        assertEquals(dialect.render(methodLast.ast), dialect.render(methodFirst.ast))
    }
}
