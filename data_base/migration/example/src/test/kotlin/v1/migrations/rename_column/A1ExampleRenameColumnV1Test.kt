package gog.my_project.data_base.migration.example.v1.migrations.rename_column

import gog.my_project.data_base.core.data_base.MARIA_DB
import gog.my_project.data_base.core.data_base.MYSQL
import gog.my_project.data_base.migration.ast.schema.rename_column.MigrationRenameColumnAst
import gog.my_project.data_base.migration.builder.ast.rename_column.MigrationRenameColumnBuilder
import gog.my_project.data_base.migration.dialect.nodes.rename_column.RenameColumnSqlStrategy
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




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
        val migration = MigrationRenameColumnBuilder().apply {
            tableName(" user`data ")
            name(" display`name ")
            to(" full`name ")
        }
        assertEquals("user`data", migration.ast.metadata.tableName)
        assertEquals("display`name", migration.ast.metadata.name)
        assertEquals("full`name", migration.ast.metadata.to)
        assertEquals(
            "ALTER TABLE `user``data` RENAME COLUMN `display``name` TO `full``name`",
            MySqlDialect().render(migration.ast),
        )
        assertEquals("ALTER TABLE `users` RENAME COLUMN `display_name` TO `full_name`",
            MySqlDialect().render(A1ExampleRenameColumnV1().migration().ast))
    }

    @Test
    fun rendererLeavesEqualAndBlankIdentifiersForDatabaseValidation() {
        val dialect = MySqlDialect()
        val equalSourceFirst = MigrationRenameColumnBuilder().apply { tableName("users"); name(" display_name "); to("display_name") }
        val equalTargetFirst = MigrationRenameColumnBuilder().apply { tableName("users"); to("display_name"); name(" display_name ") }
        assertEquals("ALTER TABLE `users` RENAME COLUMN `display_name` TO `display_name`", dialect.render(equalSourceFirst.ast))
        assertEquals("ALTER TABLE `users` RENAME COLUMN `display_name` TO `display_name`", dialect.render(equalTargetFirst.ast))
        assertEquals(
            "ALTER TABLE `` RENAME COLUMN `source` TO `target`",
            dialect.render(MigrationRenameColumnBuilder().apply { tableName("   "); name("source"); to("target") }.ast),
        )
        assertEquals(
            "ALTER TABLE `users` RENAME COLUMN `` TO `target`",
            dialect.render(MigrationRenameColumnBuilder().apply { tableName("users"); name("  "); to("target") }.ast),
        )
        assertEquals(
            "ALTER TABLE `users` RENAME COLUMN `source` TO ``",
            dialect.render(MigrationRenameColumnBuilder().apply { tableName("users"); name("source"); to("  ") }.ast),
        )
    }

    @Test
    fun rendererPassesEqualNamesButRequiresTheSourceColumn() {
        val dialect = MySqlDialect()
        val ast = MigrationRenameColumnAst().apply {
            metadata.tableName = "users"; metadata.name = " email "; metadata.to = "email"
        }
        assertEquals("ALTER TABLE `users` RENAME COLUMN ` email ` TO `email`", dialect.render(ast))
        ast.metadata.to = "new_email"
        ast.metadata.tableName = " "
        assertEquals("ALTER TABLE ` ` RENAME COLUMN ` email ` TO `new_email`", dialect.render(ast))
        ast.metadata.tableName = "users"
        ast.metadata.name = null
        assertFailsWith<IllegalArgumentException> { dialect.render(ast) }
    }
}
