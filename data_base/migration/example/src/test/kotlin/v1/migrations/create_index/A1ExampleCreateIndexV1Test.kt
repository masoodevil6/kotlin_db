package gog.my_project.data_base.migration.example.v1.migrations.create_index

import gog.my_project.data_base.migration.ast.schema.create_index.MigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.builder.createIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
        val migration = createIndex {
            tableName("  Users  ")
            name("  Idx_User_Email  ")
            column("  Email  ")
        }

        assertEquals("Users", migration.ast.tableName)
        assertEquals("Idx_User_Email", migration.ast.indexName)
        assertEquals("Email", migration.ast.columnName)
        assertEquals(
            "CREATE INDEX `Idx_User_Email` ON `Users` (`Email`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rejectsBlankIdentifiersInBuilderAndDirectAstInRenderer() {
        assertFailsWith<IllegalArgumentException> {
            createIndex { tableName(" "); name("idx"); column("email") }
        }
        assertFailsWith<IllegalArgumentException> {
            createIndex { tableName("users"); name(" "); column("email") }
        }
        assertFailsWith<IllegalArgumentException> {
            createIndex { tableName("users"); name("idx"); column(" ") }
        }

        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateIndexAst().apply {
                tableName = "users"
                indexName = "idx_users_email"
            })
        }
    }

    @Test
    fun escapesAllIdentifierPositionsAndDoesNotAddUnique() {
        val migration = createIndex {
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
        val first = createIndex {
            column("email"); name("idx_users_email"); tableName("users")
        }
        val second = createIndex {
            tableName("users"); name("idx_users_email"); column("email")
        }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }

    @Test
    fun rendersOptionalBtreeAndHashMethods() {
        val btree = createIndex {
            tableName("lookup")
            name("idx_lookup_code")
            column("code")
            using(IndexMethod.BTREE)
        }
        val hash = createIndex {
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
        val methodFirst = createIndex {
            using(IndexMethod.HASH)
            column("code")
            name("idx_lookup_code")
            tableName("lookup")
        }
        val methodLast = createIndex {
            tableName("lookup")
            name("idx_lookup_code")
            column("code")
            using(IndexMethod.HASH)
        }

        assertEquals(dialect.render(methodLast.ast), dialect.render(methodFirst.ast))
    }
}
