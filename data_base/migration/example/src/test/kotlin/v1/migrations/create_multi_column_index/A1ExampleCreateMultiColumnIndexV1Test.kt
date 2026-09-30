package gog.my_project.data_base.migration.example.v1.migrations.create_multi_column_index

import gog.my_project.data_base.migration.ast.schema.create_multi_column_index.MigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.builder.createMultiColumnIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class A1ExampleCreateMultiColumnIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersOrderedNonUniqueIndex() {
        assertEquals(
            "CREATE INDEX `idx_users_name_email` ON `users` (`last_name`, `first_name`, `email`)",
            dialect.render(A1ExampleCreateMultiColumnIndexV1().migration().ast),
        )
    }

    @Test
    fun trimsAndPreservesIdentifierCasingAndColumnOrder() {
        val migration = createMultiColumnIndex {
            tableName("  Users  ")
            name("  Idx_User_Name  ")
            columns("  Last_Name  ", "  First_Name  ", "  Email  ")
        }

        assertEquals("Users", migration.ast.tableName)
        assertEquals("Idx_User_Name", migration.ast.indexName)
        assertEquals(listOf("Last_Name", "First_Name", "Email"), migration.ast.columnNames)
        assertEquals(
            "CREATE INDEX `Idx_User_Name` ON `Users` (`Last_Name`, `First_Name`, `Email`)",
            dialect.render(migration.ast),
        )
        assertEquals(0, migration.params.size)
    }

    @Test
    fun laterColumnsCallReplacesEarlierList() {
        val migration = createMultiColumnIndex {
            tableName("users")
            name("idx_users_email_age")
            columns("last_name", "first_name")
            columns("email", "age")
        }

        assertEquals(listOf("email", "age"), migration.ast.columnNames)
        assertEquals(
            "CREATE INDEX `idx_users_email_age` ON `users` (`email`, `age`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun duplicateComparisonIgnoresCaseAndWhitespaceButPreservesValidSpelling() {
        assertFailsWith<IllegalArgumentException> {
            createMultiColumnIndex {
                tableName("users")
                name("idx_users_email")
                columns("email", " EMAIL ")
            }
        }

        val valid = createMultiColumnIndex {
            tableName("users")
            name("idx_users_email_user")
            columns("Email", "UserName")
        }
        assertEquals(listOf("Email", "UserName"), valid.ast.columnNames)
        assertEquals(
            "CREATE INDEX `idx_users_email_user` ON `users` (`Email`, `UserName`)",
            dialect.render(valid.ast),
        )
    }

    @Test
    fun hasNoArtificialColumnCountLimit() {
        val columns = (1..20).map { "column_$it" }
        val migration = createMultiColumnIndex {
            tableName("events")
            name("idx_events_many")
            columns(*columns.toTypedArray())
        }

        assertEquals(columns, migration.ast.columnNames)
        assertEquals(
            "CREATE INDEX `idx_events_many` ON `events` (${columns.joinToString(", ") { "`$it`" }})",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rejectsMissingBlankOrSingleColumnInput() {
        assertFailsWith<IllegalArgumentException> {
            createMultiColumnIndex { tableName("users"); name("idx") }
        }
        assertFailsWith<IllegalArgumentException> {
            createMultiColumnIndex { tableName("users"); name("idx"); columns("email") }
        }
        assertFailsWith<IllegalArgumentException> {
            createMultiColumnIndex { tableName("users"); name("idx"); columns("email", " ") }
        }
    }

    @Test
    fun rendererRejectsInvalidDirectAst() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateMultiColumnIndexAst().apply {
                tableName = "users"
                indexName = "idx_users_email"
                columnNames = listOf("email")
            })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateMultiColumnIndexAst().apply {
                tableName = "users"
                indexName = "idx_users_email"
                columnNames = listOf("email", " EMAIL ")
            })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateMultiColumnIndexAst().apply {
                tableName = "users"
                indexName = "idx_users_email"
                columnNames = listOf("email", " ")
            })
        }
    }

    @Test
    fun quotesReservedWordsEscapesBackticksAndAddsNoOptions() {
        val sql = createMultiColumnIndex {
            tableName("order")
            name("select`index")
            columns("user`email", "group")
        }.let { dialect.render(it.ast)!! }

        assertEquals("CREATE INDEX `select``index` ON `order` (`user``email`, `group`)", sql)
        assertFalse(sql.contains("UNIQUE"))
        assertFalse(sql.contains("IF NOT EXISTS"))
        assertFalse(sql.startsWith("ALTER TABLE"))
    }

    @Test
    fun scalarSetterCallOrderDoesNotChangeSql() {
        val first = createMultiColumnIndex {
            columns("last_name", "first_name")
            name("idx_users_name")
            tableName("users")
        }
        val second = createMultiColumnIndex {
            tableName("users")
            name("idx_users_name")
            columns("last_name", "first_name")
        }

        assertEquals(dialect.render(second.ast), dialect.render(first.ast))
    }
}
