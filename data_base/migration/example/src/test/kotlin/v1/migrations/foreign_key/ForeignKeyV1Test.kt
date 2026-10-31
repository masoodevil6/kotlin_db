package gog.my_project.data_base.migration.example.v1.migrations.foreign_key

import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.ast.schema.create_foreign_key.MigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.schema.drop_foreign_key.MigrationDropForeignKeyAst
import gog.my_project.data_base.migration.builder.ast.create_foreign_key.MigrationCreateForeignKeyBuilder
import gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key.A1ExampleCreateForeignKeyV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key.A1ExampleDropForeignKeyV1
import gog.my_project.data_base.migration.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test




class ForeignKeyV1Test {
    private val mysql = MySqlDialect()
    private val mariaDb = MariaDbDialect()

    @Test
    fun rendersCompositeCreateSqlWithActionsAndPairedOrder() {
        val expected =
            "ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_tenant_user` " +
                "FOREIGN KEY (`tenant_id`, `user_id`) REFERENCES `users` (`tenant_id`, `id`) " +
                "ON DELETE CASCADE ON UPDATE RESTRICT"
        val ast = A1ExampleCreateForeignKeyV1().migration().ast
        assertEquals(expected, mysql.render(ast))
        assertEquals(expected, mariaDb.render(ast))
    }

    @Test
    fun rendersDropSqlAndMariaDbUsesInheritedRegistration() {
        val ast = A1ExampleDropForeignKeyV1().migration().ast
        assertEquals("ALTER TABLE `orders` DROP FOREIGN KEY `fk_orders_tenant_user`", mysql.render(ast))
        assertEquals(mysql.render(ast), mariaDb.render(ast))
    }

    @Test
    fun trimsIdentifiersAndPreservesCaseAndPositionalColumnOrder() {
        val migration = MigrationCreateForeignKeyBuilder().apply {
            tableName(" Orders ")
            name(" Fk_Order_User ")
            columns(" User_ID ", "Tenant_ID")
            referencesTable(" Users ")
            referencesColumns(" ID ", "Tenant_ID")
        }

        assertEquals("Orders", migration.ast.metadata.tableName)
        assertEquals("Fk_Order_User", migration.ast.metadata.constraintName)
        assertEquals(listOf("User_ID", "Tenant_ID"), migration.ast.metadata.columnNames)
        assertEquals(listOf("ID", "Tenant_ID"), migration.ast.metadata.referencesColumnNames)
        assertEquals(
            "ALTER TABLE `Orders` ADD CONSTRAINT `Fk_Order_User` " +
                "FOREIGN KEY (`User_ID`, `Tenant_ID`) REFERENCES `Users` (`ID`, `Tenant_ID`)",
            mysql.render(migration.ast),
        )
    }

    @Test
    fun escapesIdentifiersAndSupportsSingleColumnAsOnePair() {
        val migration = MigrationCreateForeignKeyBuilder().apply {
            tableName("order`data")
            name("fk`order")
            columns("user`id")
            referencesTable("user`data")
            referencesColumns("id`key")
        }

        assertEquals(
            "ALTER TABLE `order``data` ADD CONSTRAINT `fk``order` " +
                "FOREIGN KEY (`user``id`) REFERENCES `user``data` (`id``key`)",
            mysql.render(migration.ast),
        )
    }

    @Test
    fun omittedActionsProduceNoActionClausesAndEveryContractActionRenders() {
        val omitted = foreignKey().ast
        assertEquals(
            "ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_user` " +
                "FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)",
            mysql.render(omitted),
        )

        val actionSql = mapOf(
            ForeignKeyAction.CASCADE to "CASCADE",
            ForeignKeyAction.RESTRICT to "RESTRICT",
            ForeignKeyAction.NO_ACTION to "NO ACTION",
            ForeignKeyAction.SET_NULL to "SET NULL",
        )
        actionSql.forEach { (action, sql) ->
            val withDelete = MigrationCreateForeignKeyBuilder().apply {
                tableName("orders"); name("fk_orders_user"); columns("user_id")
                referencesTable("users"); referencesColumns("id"); onDelete(action)
            }
            assertEquals(true, mysql.render(withDelete.ast)!!.endsWith("ON DELETE $sql"))

            val withUpdate = MigrationCreateForeignKeyBuilder().apply {
                tableName("orders"); name("fk_orders_user"); columns("user_id")
                referencesTable("users"); referencesColumns("id"); onUpdate(action)
            }
            assertEquals(true, mysql.render(withUpdate.ast)!!.endsWith("ON UPDATE $sql"))
        }
    }

    @Test
    fun rendersBlankUnequalAndDuplicateInputsForDatabaseValidation() {
        assertFailsWith<IllegalArgumentException> {
            mysql.render(MigrationCreateForeignKeyBuilder().apply { name("fk"); columns("a"); referencesTable("p"); referencesColumns("a") }.ast)
        }
        assertEquals("ALTER TABLE `` ADD CONSTRAINT `fk` FOREIGN KEY (`a`) REFERENCES `p` (`a`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName(" "); name("fk"); columns("a"); referencesTable("p"); referencesColumns("a") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `` FOREIGN KEY (`a`) REFERENCES `p` (`a`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name(" "); columns("a"); referencesTable("p"); referencesColumns("a") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (``) REFERENCES `p` (`a`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns(" "); referencesTable("p"); referencesColumns("a") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (`a`) REFERENCES `` (`a`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns("a"); referencesTable(" "); referencesColumns("a") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (`a`) REFERENCES `p` (``)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns("a"); referencesTable("p"); referencesColumns(" ") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (`a`, `b`) REFERENCES `p` (`a`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns("a", "b"); referencesTable("p"); referencesColumns("a") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (`a`, `A`) REFERENCES `p` (`a`, `b`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns("a", " A "); referencesTable("p"); referencesColumns("a", "b") }.ast))
        assertEquals("ALTER TABLE `c` ADD CONSTRAINT `fk` FOREIGN KEY (`a`, `b`) REFERENCES `p` (`b`, `B`)", mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns("a", "b"); referencesTable("p"); referencesColumns("b", " B ") }.ast))
        assertFailsWith<IllegalArgumentException> { mysql.render(MigrationCreateForeignKeyBuilder().apply { tableName("c"); name("fk"); columns(); referencesTable("p"); referencesColumns() }.ast) }
    }

    @Test
    fun repeatedColumnSettersReplaceListsAndKeepLatestOrder() {
        val migration = MigrationCreateForeignKeyBuilder().apply {
            tableName("orders"); name("fk_orders_user")
            columns("discarded"); columns("tenant_id", "user_id")
            referencesTable("users")
            referencesColumns("discarded"); referencesColumns("tenant_id", "id")
        }
        assertEquals(listOf("tenant_id", "user_id"), migration.ast.metadata.columnNames)
        assertEquals(listOf("tenant_id", "id"), migration.ast.metadata.referencesColumnNames)
    }

    @Test
    fun renderersRequireAColumnListAndPassSemanticShapeToDatabase() {
        assertFailsWith<IllegalArgumentException> { mysql.render(MigrationCreateForeignKeyAst()) }
        assertEquals(
            "ALTER TABLE `orders` ADD CONSTRAINT `fk` FOREIGN KEY (`a`) REFERENCES `users` (`a`, `b`)",
            mysql.render(MigrationCreateForeignKeyAst().apply {
                metadata.tableName = "orders"; metadata.constraintName = "fk"; metadata.columnNames = listOf("a")
                metadata.referencesTableName = "users"; metadata.referencesColumnNames = listOf("a", "b")
            }),
        )
        assertEquals(
            "ALTER TABLE `orders` ADD CONSTRAINT `fk` FOREIGN KEY (`a`, ` A `) REFERENCES `users` (`a`, `b`)",
            mysql.render(MigrationCreateForeignKeyAst().apply {
                metadata.tableName = "orders"; metadata.constraintName = "fk"; metadata.columnNames = listOf("a", " A ")
                metadata.referencesTableName = "users"; metadata.referencesColumnNames = listOf("a", "b")
            }),
        )
        assertEquals(
            "ALTER TABLE `orders` ADD CONSTRAINT `fk` FOREIGN KEY (`a`, `b`) REFERENCES `users` (`a`, ` A `)",
            mysql.render(MigrationCreateForeignKeyAst().apply {
                metadata.tableName = "orders"; metadata.constraintName = "fk"; metadata.columnNames = listOf("a", "b")
                metadata.referencesTableName = "users"; metadata.referencesColumnNames = listOf("a", " A ")
            }),
        )
        assertFailsWith<IllegalArgumentException> { mysql.render(MigrationDropForeignKeyAst()) }
        assertEquals("ALTER TABLE `orders` DROP FOREIGN KEY ` `", mysql.render(MigrationDropForeignKeyAst().apply { metadata.tableName = "orders"; metadata.constraintName = " " }))
    }

    private fun foreignKey() = MigrationCreateForeignKeyBuilder().apply {
        tableName("orders")
        name("fk_orders_user")
        columns("user_id")
        referencesTable("users")
        referencesColumns("id")
    }
}
