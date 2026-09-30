package gog.my_project.data_base.migration.example.v1.migrations.rename_column

import gog.my_project.data_base.migration.ast.schema.rename_column.MigrationRenameColumnAst
import gog.my_project.data_base.migration.builder.renameColumn
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.migration.dialect.nodes.rename_column.RenameColumnSqlStrategy
import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.data_base.MYSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class A1ExampleRenameColumnV1Test {
    @Test
    fun targetVersionControlsWhetherNativeRenameGrammarCanBeGenerated() {
        val migration = A1ExampleRenameColumnV1().migration()
        val legacyMaria = MariaDbDialect(MARIA_DB.Version(10, 4, 28))
        assertEquals(RenameColumnSqlStrategy.CHANGE_COLUMN, legacyMaria.renameColumnSqlStrategy)
        assertFailsWith<IllegalStateException> {
            legacyMaria.render(migration.ast)
        }
        val legacyMysql = MySqlDialect(MYSQL.Version(5, 7, 44))
        assertEquals(RenameColumnSqlStrategy.CHANGE_COLUMN, legacyMysql.renameColumnSqlStrategy)
        assertFailsWith<IllegalStateException> {
            legacyMysql.render(migration.ast)
        }
        assertEquals(
            "ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`",
            MariaDbDialect(MARIA_DB.Version(10, 5, 3)).render(migration.ast),
        )
        assertEquals(
            "ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`",
            MySqlDialect(MYSQL.Version(8, 0, 36)).render(migration.ast),
        )
    }

    @Test
    fun rendersRenameOnlyWithEscapedIdentifiers() {
        val migration = renameColumn {
            tableName(" user`data ")
            name(" display`name ")
            to(" full`name ")
        }
        assertEquals("user`data", migration.ast.tableName)
        assertEquals("display`name", migration.ast.name)
        assertEquals("full`name", migration.ast.to)
        assertEquals(
            "ALTER TABLE `user``data` RENAME COLUMN `display``name` TO `full``name`",
            MySqlDialect().render(migration.ast),
        )
        assertEquals("ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`",
            MySqlDialect().render(A1ExampleRenameColumnV1().migration().ast))
    }

    @Test
    fun builderRejectsEqualNamesAfterTrimInEitherSetterOrder() {
        assertFailsWith<IllegalArgumentException> {
            renameColumn { tableName("users"); name(" display_name "); to("display_name") }
        }
        assertFailsWith<IllegalArgumentException> {
            renameColumn { tableName("users"); to("display_name"); name(" display_name ") }
        }
        assertFailsWith<IllegalArgumentException> { renameColumn { tableName("   ") } }
        assertFailsWith<IllegalArgumentException> { renameColumn { name("  ") } }
        assertFailsWith<IllegalArgumentException> { renameColumn { to("  ") } }
    }

    @Test
    fun rendererRejectsDirectMalformedAst() {
        val dialect = MySqlDialect()
        val ast = MigrationRenameColumnAst().apply { tableName = "users"; name = " email "; to = "email" }
        assertFailsWith<IllegalArgumentException> { dialect.render(ast) }
        ast.to = "new_email"
        ast.tableName = " "
        assertFailsWith<IllegalArgumentException> { dialect.render(ast) }
        ast.tableName = "users"
        ast.name = null
        assertFailsWith<IllegalArgumentException> { dialect.render(ast) }
    }
}
