package gog.my_project.data_base.migration.example.v1.migrations.create_table

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.builder.ast.create_table.render_migration_create_table.MigrationRenderCreateTableBuilder
import gog.my_project.data_base.migration.example.managers.ManagerExampleV1
import gog.my_project.data_base.migration.example.v1.migrations.create_table_constraints.A1ExampleCreateTableConstraintsV1
import gog.my_project.data_base.migration.example.v1.migrations.create_table_timestamps.A1ExampleCreateTableTimestampsV1
import gog.my_project.data_base.migration.params.data_types.DateTimeType
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.TextType
import gog.my_project.data_base.migration.params.data_types.VarcharType
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test




class A1ExampleCreateTableV1Test {
    @Test
    fun rendersUsersTableForMySql() {
        val sql = MySqlDialect().render(A1ExampleCreateTableV1().migration().ast)

        assertEquals(
            """CREATE TABLE `users` (
  `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(120) NOT NULL,
  `display_name` VARCHAR(120) NOT NULL DEFAULT 'New user'
)""",
            sql,
        )
    }

    @Test
    fun includesAllExpectedColumns() {
        val sql = MySqlDialect().render(A1ExampleCreateTableV1().migration().ast)

        assertContains(sql.orEmpty(), "`id`")
        assertContains(sql.orEmpty(), "`name`")
        assertContains(sql.orEmpty(), "`display_name`")
    }

    @Test
    fun rendersCreateTableWithIfNotExists() {
        val sql = MySqlDialect().render(
            A1ExampleCreateTableV1(useIfNotExists = true).migration().ast,
        )

        assertEquals(true, sql?.startsWith("CREATE TABLE IF NOT EXISTS `users` ("))
    }

    @Test
    fun ifNotExistsDefaultsToFalseInAst() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("users") }
        }

        assertEquals(false, migration.ast.metadata.ifNotExists)
    }

    @Test
    fun rejectsCreateTableWithoutColumns() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("empty_table") }
        }

        assertFailsWith<IllegalArgumentException> {
            MySqlDialect().render(migration.ast)
        }
    }

    @Test
    fun rendersAutoIncrementWithoutRevalidatingPrimaryKeyRules() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("invalid_table") }
            addColumn {
                name("id")
                dataType(gog.my_project.data_base.migration.params.data_types.IntType())
                notNull()
                autoIncrement()
            }
        }

        assertContains(MySqlDialect().render(migration.ast).orEmpty(), "`id` INT NOT NULL AUTO_INCREMENT")
    }

    @Test
    fun rendersTableLevelDefinitionsInDslOrderAndKeepsLegacyColumnsFirst() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("orders") }
            addColumn { name("tenant_id"); dataType(IntType()); notNull() }
            addColumn { name("order_id"); dataType(IntType()); notNull() }
            addColumn { name("user_id"); dataType(IntType()); notNull() }
            addColumn { name("email"); dataType(VarcharType(255)); notNull() }
            addColumn { name("description"); dataType(TextType()); notNull() }
            primaryKey { columns("tenant_id", "order_id") }
            unique { name("uq_orders_email"); columns("email") }
            index {
                name("idx_orders_tenant_user")
                columns("tenant_id", "user_id")
                using(IndexMethod.BTREE)
            }
            fullTextIndex { name("ft_orders_description"); columns("description") }
            foreignKey {
                name("fk_orders_user")
                columns("tenant_id", "user_id")
                referencesTable("users")
                referencesColumns("tenant_id", "id")
                onDelete(ForeignKeyAction.CASCADE)
                onUpdate(ForeignKeyAction.RESTRICT)
            }
        }

        assertEquals(
            """CREATE TABLE `orders` (
  `tenant_id` INT NOT NULL,
  `order_id` INT NOT NULL,
  `user_id` INT NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `description` TEXT NOT NULL,
  PRIMARY KEY (`tenant_id`, `order_id`),
  CONSTRAINT `uq_orders_email` UNIQUE (`email`),
  INDEX `idx_orders_tenant_user` (`tenant_id`, `user_id`) USING BTREE,
  FULLTEXT INDEX `ft_orders_description` (`description`),
  CONSTRAINT `fk_orders_user` FOREIGN KEY (`tenant_id`, `user_id`) REFERENCES `users` (`tenant_id`, `id`) ON DELETE CASCADE ON UPDATE RESTRICT
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun versionedCreateTableConstraintsExampleRendersAllSupportedDefinitions() {
        val sql = MySqlDialect().render(A1ExampleCreateTableConstraintsV1().migration().ast).orEmpty()

        assertContains(sql, "PRIMARY KEY (`tenant_id`, `order_id`)")
        assertContains(sql, "CONSTRAINT `uq_orders_email` UNIQUE (`email`)")
        assertContains(sql, "INDEX `idx_orders_tenant_user` (`tenant_id`, `user_id`) USING BTREE")
        assertContains(sql, "FULLTEXT INDEX `ft_orders_description` (`description`)")
        assertContains(
            sql,
            "CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT",
        )
    }

    @Test
    fun versionedTimestampsExampleRendersSchemaOnlyColumns() {
        val sql = MySqlDialect().render(A1ExampleCreateTableTimestampsV1().migration().ast)

        assertContains(sql.orEmpty(), "`created_at` DATETIME NULL")
        assertContains(sql.orEmpty(), "`updated_at` DATETIME NULL")
        assertContains(sql.orEmpty(), "`deleted_at` DATETIME NULL")
        assertTrue("CURRENT_TIMESTAMP" !in sql.orEmpty())
    }

    @Test
    fun exampleManagerRegistersVersionedCreateTableConstraintsExample() {
        val manager = ManagerExampleV1()
        manager.readyListExamples()

        assertTrue(manager.listExamplesCreateTable.any { it is A1ExampleCreateTableConstraintsV1 })
        assertTrue(manager.listExamplesCreateTable.any { it is A1ExampleCreateTableTimestampsV1 })
    }

    @Test
    fun trimsIdentifiersAndPreservesTheirCasing() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("Users") }
            addColumn { name(" Email "); dataType(VarcharType(255)); notNull() }
            unique { name(" UQ_User_Email "); columns(" Email ") }
        }

        assertEquals(
            """CREATE TABLE `Users` (
  `Email` VARCHAR(255) NOT NULL,
  CONSTRAINT `UQ_User_Email` UNIQUE (`Email`)
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rendersRepeatedAndMixedPrimaryKeyDefinitionsForDatabaseValidation() {
        val twoTablePrimaryKeys = MigrationRenderCreateTableBuilder().apply {
            table { tableName("invalid_table") }
            addColumn { name("id"); dataType(IntType()); notNull() }
            primaryKey { columns("id") }
            primaryKey { columns("id") }
        }
        val repeatedPrimaryKeySql = MySqlDialect().render(twoTablePrimaryKeys.ast).orEmpty()
        assertEquals(2, repeatedPrimaryKeySql.split("PRIMARY KEY").size - 1)

        val mixedPrimaryKeys = MigrationRenderCreateTableBuilder().apply {
            table { tableName("invalid_table") }
            addColumn { name("id"); dataType(IntType()); notNull(); primaryKey() }
            addColumn { name("tenant_id"); dataType(IntType()); notNull() }
            primaryKey { columns("tenant_id") }
        }
        val mixedPrimaryKeySql = MySqlDialect().render(mixedPrimaryKeys.ast).orEmpty()
        assertContains(mixedPrimaryKeySql, "`id` INT NOT NULL PRIMARY KEY")
        assertContains(mixedPrimaryKeySql, "PRIMARY KEY (`tenant_id`)")
    }

    @Test
    fun tableLevelPrimaryKeySupportsExistingAutoIncrementColumnSemantics() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("users") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
            }
            addColumn { name("tenant_id"); dataType(IntType()); notNull() }
            primaryKey { columns("tenant_id", "id") }
        }

        assertEquals(
            """CREATE TABLE `users` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `tenant_id` INT NOT NULL,
  PRIMARY KEY (`tenant_id`, `id`)
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rendersNullableColumnInTableLevelPrimaryKeyForDatabaseValidation() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("users") }
            addColumn { name("id"); dataType(IntType()); nullable() }
            primaryKey { columns("id") }
        }

        val sql = MySqlDialect().render(migration.ast).orEmpty()
        assertContains(sql, "`id` INT NULL")
        assertContains(sql, "PRIMARY KEY (`id`)")
    }

    @Test
    fun rendersBlankAndDuplicateDefinitionColumnsForDatabaseValidation() {
        val blankColumn = MigrationRenderCreateTableBuilder().apply {
            table { tableName("invalid_table") }
            addColumn { name("id"); dataType(IntType()) }
            index { name("idx_bad"); columns(" ") }
        }
        assertContains(MySqlDialect().render(blankColumn.ast).orEmpty(), "INDEX `idx_bad` (``)")

        val duplicateColumn = MigrationRenderCreateTableBuilder().apply {
            table { tableName("invalid_table") }
            addColumn { name("id"); dataType(IntType()) }
            index { name("idx_bad"); columns("id", " ID ") }
        }
        assertContains(MySqlDialect().render(duplicateColumn.ast).orEmpty(), "INDEX `idx_bad` (`id`, `ID`)")
    }

    @Test
    fun timestampsAndSoftDeletesExpandToNullableDateTimeColumnsInDslOrder() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("posts") }
            addColumn { name("id"); dataType(IntType()); notNull() }
            timestamps()
            addColumn { name("title"); dataType(VarcharType(120)); notNull() }
            softDeletes()
        }

        assertEquals(
            """CREATE TABLE `posts` (
  `id` INT NOT NULL,
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  `title` VARCHAR(120) NOT NULL,
  `deleted_at` DATETIME NULL
)""",
            MySqlDialect().render(migration.ast),
        )
        assertEquals(listOf("id", "created_at", "updated_at", "title", "deleted_at"), migration.ast.columns.map { it.metadata.columnName })
        migration.ast.columns.drop(1).filter { it.metadata.columnName in setOf("created_at", "updated_at", "deleted_at") }.forEach {
            assertEquals(DateTimeType(), it.metadata.columnDataType)
            assertEquals(true, it.metadata.columnNullable)
            assertEquals(false, it.metadata.hasColumnDefault)
            assertEquals(false, it.metadata.columnAutoIncrement)
            assertEquals(false, it.metadata.columnPrimary)
        }
    }

    @Test
    fun rendersTimestampsShorthandAlone() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("events") }
            timestamps()
        }

        assertEquals(
            """CREATE TABLE `events` (
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rendersSoftDeletesShorthandAlone() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("events") }
            softDeletes()
        }

        assertEquals(
            """CREATE TABLE `events` (
  `deleted_at` DATETIME NULL
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun shorthandColumnOrderFollowsShorthandCallOrder() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("posts") }
            softDeletes()
            timestamps()
        }

        assertEquals(listOf("deleted_at", "created_at", "updated_at"), migration.ast.columns.map { it.metadata.columnName })
        assertEquals(
            """CREATE TABLE `posts` (
  `deleted_at` DATETIME NULL,
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL
)""",
            MySqlDialect().render(migration.ast),
        )
    }

    @Test
    fun rendersManualTimestampColumnCollisionsInEitherDslOrder() {
        val manualBeforeShorthand = MigrationRenderCreateTableBuilder().apply {
            table { tableName("posts") }
            addColumn { name("CREATED_AT"); dataType(DateTimeType()) }
            timestamps()
        }
        val firstSql = MySqlDialect().render(manualBeforeShorthand.ast).orEmpty()
        assertContains(firstSql, "`CREATED_AT` DATETIME NOT NULL")
        assertContains(firstSql, "`created_at` DATETIME NULL")
        val shorthandBeforeManual = MigrationRenderCreateTableBuilder().apply {
            table { tableName("posts") }
            timestamps()
            addColumn { name(" Deleted_At "); dataType(DateTimeType()) }
            softDeletes()
        }
        val secondSql = MySqlDialect().render(shorthandBeforeManual.ast).orEmpty()
        assertContains(secondSql, "`deleted_at` DATETIME NULL")
        assertContains(secondSql, "`Deleted_At` DATETIME NOT NULL")
    }

    @Test
    fun rendersRepeatedTimestampShorthandsAndBlankColumnName() {
        val repeatedTimestamps = MigrationRenderCreateTableBuilder().apply { table { tableName("posts") }; timestamps(); timestamps() }
        val repeatedTimestampSql = MySqlDialect().render(repeatedTimestamps.ast).orEmpty()
        assertEquals(2, repeatedTimestampSql.split("`created_at`").size - 1)
        assertEquals(2, repeatedTimestampSql.split("`updated_at`").size - 1)
        val repeatedSoftDeletes = MigrationRenderCreateTableBuilder().apply { table { tableName("posts") }; softDeletes(); softDeletes() }
        assertEquals(2, MySqlDialect().render(repeatedSoftDeletes.ast).orEmpty().split("`deleted_at`").size - 1)
        val blankColumn = MigrationRenderCreateTableBuilder().apply { table { tableName("posts") }; addColumn { name(" "); dataType(IntType()) } }
        assertContains(MySqlDialect().render(blankColumn.ast).orEmpty(), "`` INT NOT NULL")
    }

    @Test
    fun rendererPreservesCaseInsensitiveDuplicateColumnNamesInDirectAst() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("posts") }
            addColumn { name("created_at"); dataType(DateTimeType()); nullable() }
            addColumn { name("title"); dataType(VarcharType(120)) }
        }
        migration.ast.columns[1].metadata.columnName = " CREATED_AT "

        val sql = MySqlDialect().render(migration.ast).orEmpty()
        assertContains(sql, "` CREATED_AT ` VARCHAR(120) NOT NULL")
        assertContains(sql, "`created_at` DATETIME NULL")
    }

    @Test
    fun rendersMismatchedForeignKeyListsForDatabaseValidation() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("orders") }
            addColumn { name("user_id"); dataType(IntType()) }
            foreignKey {
                name("fk_orders_user")
                columns("user_id")
                referencesTable("users")
                referencesColumns("id")
            }
        }
        migration.ast.definitions += gog.my_project.data_base.migration.ast.interfaces.create_table.definition.ForeignKeyDefinitionAst(
            gog.my_project.data_base.migration.ast.metadata.create_table.definition.ForeignKeyDefinitionAstMetadata(
                constraintName = "fk_bad",
                columnNames = listOf("user_id"),
                referencesTableName = "users",
                referencesColumnNames = listOf("id", "other_id"),
            ),
        )

        assertContains(MySqlDialect().render(migration.ast).orEmpty(), "REFERENCES `users` (`id`, `other_id`)")
    }

    @Test
    fun escapesBackticksInTableAndDefinitionIdentifiers() {
        val migration = MigrationRenderCreateTableBuilder().apply {
            table { tableName("order`data") }
            addColumn { name("user`id"); dataType(IntType()) }
            index { name("idx`orders"); columns("user`id") }
        }

        assertContains(MySqlDialect().render(migration.ast).orEmpty(), "`order``data`")
        assertContains(MySqlDialect().render(migration.ast).orEmpty(), "`idx``orders` (`user``id`)")
    }
}
