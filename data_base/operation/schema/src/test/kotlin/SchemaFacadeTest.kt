package gog.my_project.data_base_schema

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst
import gog.my_project.data_base_schema.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base_schema.ast.schema.if_exist.SchemaIfExistAst
import gog.my_project.data_base_schema.renderer.dialects.MariaDbDialect
import gog.my_project.data_base_schema.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaFacadeTest {
    @Test
    fun `all fifteen public factories create one opaque operation and render through the facade`() {
        val operations = listOf(
            Schema.createTable {
                table { tableName("customers") }
                addColumn { name("id"); dataType(SchemaDataType.IntType); notNull(); autoIncrement(); primaryKey() }
                addColumn { name("email"); dataType(SchemaDataType.VarcharType(180)); default(null) }
                primaryKey { columns("id") }
                unique { name("uq_email"); columns("email") }
                index { name("idx_email"); columns("email"); using(SchemaIndexMethod.BTREE) }
                fullTextIndex { name("ft_email"); columns("email") }
                foreignKey {
                    name("fk_customer")
                    columns("id")
                    referencesTable("customer_accounts")
                    referencesColumns("id")
                    onDelete(SchemaForeignKeyAction.CASCADE)
                    onUpdate(SchemaForeignKeyAction.RESTRICT)
                }
                ifNotExists()
            } to "CREATE TABLE",
            Schema.dropTable { tableName("customers"); ifExists() } to "DROP TABLE",
            Schema.renameTable { fromTableName("customers"); toTableName("clients") } to "RENAME TABLE",
            Schema.addColumn { tableName("customers"); name("phone"); dataType(SchemaDataType.VarcharType(32)); nullable() } to "ADD COLUMN",
            Schema.dropColumn { tableName("customers"); name("phone") } to "DROP COLUMN",
            Schema.renameColumn { tableName("customers"); name("phone"); to("mobile") } to "RENAME COLUMN",
            Schema.modifyColumn { tableName("customers"); name("mobile"); dataType(SchemaDataType.VarcharType(48)); notNull() } to "MODIFY COLUMN",
            Schema.createIndex { tableName("customers"); name("idx_phone"); column("phone"); using(SchemaIndexMethod.HASH) } to "CREATE INDEX",
            Schema.dropIndex { tableName("customers"); name("idx_phone") } to "DROP INDEX",
            Schema.createUniqueIndex { tableName("customers"); name("uq_phone"); column("phone") } to "CREATE UNIQUE INDEX",
            Schema.createMultiColumnIndex { tableName("customers"); name("idx_name"); columns("first_name", "last_name") } to "CREATE INDEX",
            Schema.createFullTextIndex { tableName("customers"); name("ft_name"); columns("first_name", "last_name") } to "FULLTEXT",
            Schema.createSpatialIndex { tableName("places"); name("sp_location"); column("location") } to "SPATIAL",
            Schema.createForeignKey {
                tableName("orders")
                name("fk_order_customer")
                columns("customer_id")
                referencesTable("customers")
                referencesColumns("id")
                onDelete(SchemaForeignKeyAction.CASCADE)
            } to "FOREIGN KEY",
            Schema.dropForeignKey { tableName("orders"); name("fk_order_customer") } to "DROP FOREIGN KEY",
        )

        assertEquals(15, operations.size)
        operations.forEach { (operation, expectedFragment) ->
            assertIs<SchemaOperation>(operation)
            val adapter = assertIs<ExistingSchemaOperationAdapter>(operation)
            assertNotNull(adapter.schemaApi.ast)
            val sql = Schema.render(operation, SchemaDialect.MYSQL)
            assertTrue(sql.contains(expectedFragment, ignoreCase = true), "Expected '$expectedFragment' in: $sql")
        }

        val createAst = assertIs<ISchemaRenderCreateTableAst>(
            assertIs<ExistingSchemaOperationAdapter>(operations.first().first).schemaApi.ast,
        )
        assertEquals(2, createAst.columns.size)
        assertEquals(5, createAst.definitions.size)
        assertTrue(Schema.render(operations.first().first, SchemaDialect.MARIA_DB).startsWith("CREATE TABLE IF NOT EXISTS"))
    }

    @Test
    fun `ifExists node renders independently and is attached to drop table`() {
        val ifExistAst = SchemaIfExistAst()
        assertEquals("IF EXISTS", MySqlDialect().render(ifExistAst))
        assertEquals("IF EXISTS", MariaDbDialect().render(ifExistAst))

        val ordinary = Schema.dropTable { tableName("customers") }
        val conditional = Schema.dropTable { tableName("customers"); ifExists() }
        val ordinaryAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst>(
            assertIs<ExistingSchemaOperationAdapter>(ordinary).schemaApi.ast,
        )
        val conditionalAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst>(
            assertIs<ExistingSchemaOperationAdapter>(conditional).schemaApi.ast,
        )

        assertNull(ordinaryAst.ifExistAst)
        assertNotNull(conditionalAst.ifExistAst)
        SchemaDialect.values().forEach { dialect ->
            assertEquals("DROP TABLE `customers`", Schema.render(ordinary, dialect))
            assertEquals("DROP TABLE IF EXISTS `customers`", Schema.render(conditional, dialect))
        }
    }

    @Test
    fun `drop column ifExists is stored and rendered only for supporting dialects`() {
        val ordinary = Schema.dropColumn { tableName("customers"); name("phone") }
        val conditional = Schema.dropColumn { tableName("customers"); name("phone"); ifExists() }
        val ordinaryAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst>(
            assertIs<ExistingSchemaOperationAdapter>(ordinary).schemaApi.ast,
        )
        val conditionalAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst>(
            assertIs<ExistingSchemaOperationAdapter>(conditional).schemaApi.ast,
        )

        assertNull(ordinaryAst.ifExistAst)
        assertNotNull(conditionalAst.ifExistAst)
        SchemaDialect.values().forEach { dialect ->
            assertEquals(
                "ALTER TABLE `customers` DROP COLUMN `phone`",
                Schema.render(ordinary, dialect),
            )
        }
        assertEquals(
            "ALTER TABLE `customers` DROP COLUMN IF EXISTS `phone`",
            Schema.render(conditional, SchemaDialect.MARIA_DB),
        )
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MYSQL)
        }
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 0, 1))
        }
        assertEquals(
            "ALTER TABLE `customers` DROP COLUMN IF EXISTS `phone`",
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 0, 2)),
        )
    }

    @Test
    fun `drop index ifExists is stored and rendered only for MariaDB versions supporting the statement`() {
        val ordinary = Schema.dropIndex { tableName("customers"); name("idx_phone") }
        val conditional = Schema.dropIndex { tableName("customers"); name("idx_phone"); ifExists() }
        val ordinaryAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst>(
            assertIs<ExistingSchemaOperationAdapter>(ordinary).schemaApi.ast,
        )
        val conditionalAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst>(
            assertIs<ExistingSchemaOperationAdapter>(conditional).schemaApi.ast,
        )

        assertNull(ordinaryAst.ifExistAst)
        assertNotNull(conditionalAst.ifExistAst)
        SchemaDialect.values().forEach { dialect ->
            assertEquals(
                "DROP INDEX `idx_phone` ON `customers`",
                Schema.render(ordinary, dialect),
            )
        }
        assertEquals(
            "DROP INDEX IF EXISTS `idx_phone` ON `customers`",
            Schema.render(conditional, SchemaDialect.MARIA_DB),
        )
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MYSQL)
        }
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 1, 3))
        }
        assertEquals(
            "DROP INDEX IF EXISTS `idx_phone` ON `customers`",
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 1, 4)),
        )
    }

    @Test
    fun `drop foreign key ifExists is stored and rendered only for MariaDB versions supporting the clause`() {
        val ordinary = Schema.dropForeignKey { tableName("orders"); name("fk_customer") }
        val conditional = Schema.dropForeignKey { tableName("orders"); name("fk_customer"); ifExists() }
        val ordinaryAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst>(
            assertIs<ExistingSchemaOperationAdapter>(ordinary).schemaApi.ast,
        )
        val conditionalAst = assertIs<gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst>(
            assertIs<ExistingSchemaOperationAdapter>(conditional).schemaApi.ast,
        )

        assertNull(ordinaryAst.ifExistAst)
        assertNotNull(conditionalAst.ifExistAst)
        SchemaDialect.values().forEach { dialect ->
            assertEquals(
                "ALTER TABLE `orders` DROP FOREIGN KEY `fk_customer`",
                Schema.render(ordinary, dialect),
            )
        }
        assertEquals(
            "ALTER TABLE `orders` DROP FOREIGN KEY IF EXISTS `fk_customer`",
            Schema.render(conditional, SchemaDialect.MARIA_DB),
        )
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MYSQL)
        }
        assertFailsWith<UnsupportedOperationException> {
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 0, 1))
        }
        assertEquals(
            "ALTER TABLE `orders` DROP FOREIGN KEY IF EXISTS `fk_customer`",
            Schema.render(conditional, SchemaDialect.MARIA_DB, SchemaVersion(SchemaDialect.MARIA_DB, 10, 0, 2)),
        )
    }

    @Test
    fun `explicit null default remains distinct from an unset default`() {
        val explicitNull = Schema.createTable {
            table { tableName("defaults") }
            addColumn { name("explicit_null"); dataType(SchemaDataType.VarcharType(8)); default(null) }
            addColumn { name("unset_default"); dataType(SchemaDataType.VarcharType(8)) }
        }
        val ast = assertIs<ISchemaRenderCreateTableAst>(
            assertIs<ExistingSchemaOperationAdapter>(explicitNull).schemaApi.ast,
        )
        assertTrue(ast.columns[0].metadata.hasColumnDefault)
        assertEquals(null, ast.columns[0].metadata.columnDefault)
        assertFalse(ast.columns[1].metadata.hasColumnDefault)

        val modifyExplicitNull = Schema.modifyColumn {
            tableName("defaults")
            name("nullable_default")
            dataType(SchemaDataType.VarcharType(8))
            default(null)
        }
        val modifyUnset = Schema.modifyColumn {
            tableName("defaults")
            name("no_default")
            dataType(SchemaDataType.VarcharType(8))
        }
        assertEquals(
            DefaultDefinition.DefaultNull,
            assertIs<ExistingSchemaOperationAdapter>(modifyExplicitNull).schemaApi.ast.let {
                (it as gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst)
                    .metadata.defaultDefinition
            },
        )
        assertEquals(
            DefaultDefinition.NoDefaultClause,
            (assertIs<ExistingSchemaOperationAdapter>(modifyUnset).schemaApi.ast as
                gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst)
                .metadata.defaultDefinition,
        )
    }

    @Test
    fun `dialect version validation and renderer null contract are explicit`() {
        val rename = Schema.renameColumn { tableName("items"); name("old_name"); to("new_name") }
        assertTrue(Schema.render(rename, SchemaDialect.MYSQL).contains("RENAME COLUMN"))
        assertFailsWith<IllegalArgumentException> {
            Schema.render(rename, SchemaDialect.MYSQL, SchemaVersion(SchemaDialect.MARIA_DB, 10, 4, 0))
        }
        assertFailsWith<IllegalArgumentException> {
            SchemaVersion(SchemaDialect.MYSQL, -1, 0, 0)
        }
        val nullRender = assertFailsWith<IllegalArgumentException> { requireSchemaSql(null) }
        assertEquals("Schema renderer returned no SQL for the supplied operation", nullRender.message)
    }

    @Test
    fun `scoped execution sends rendered DDL without parameter bindings`() {
        val operation = Schema.createTable {
            table { tableName("customers") }
            addColumn { name("note"); dataType(SchemaDataType.VarcharType(12)); default("O'Reilly") }
        }
        var capturedSql: String? = null
        var capturedParameters: List<OperationParameter>? = null
        var capturedInfo: Map<String, Any?>? = null
        var failCalls = 0
        val context = object : OperationExecutionContext {
            override fun executeDdl(sql: String, parameters: List<OperationParameter>): ExecuteResult<Boolean> {
                capturedSql = sql
                capturedParameters = parameters
                return ExecuteResult.Success(true)
            }

            override fun <T> failed(failure: Throwable): ExecuteResult<T> {
                failCalls++
                return ExecuteResult.Failure(failure)
            }
        }

        val result = Schema.execute(operation, context) { _, parameters -> capturedInfo = parameters }
        assertEquals(ExecuteResult.Success(true), result)
        assertTrue(capturedSql.orEmpty().startsWith("CREATE TABLE"))
        assertTrue(capturedSql.orEmpty().contains("DEFAULT 'O''Reilly'"))
        assertEquals(emptyList(), capturedParameters)
        assertEquals(emptyMap(), capturedInfo)
        assertEquals(0, failCalls)
    }

    @Test
    fun `public facade signatures do not expose implementation types`() {
        val forbidden = listOf(".ast.", ".builder.", ".renderer.", "SqlParameter", "BuiltQuery", "SchemaColumnDataType")
        val publicSchemaSignatures = Schema::class.java.methods
            .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            .flatMap { method -> listOf(method.genericReturnType.typeName) + method.genericParameterTypes.map { it.typeName } }
        assertTrue(publicSchemaSignatures.none { signature -> forbidden.any(signature::contains) }, publicSchemaSignatures.joinToString("\n"))
        assertTrue(SchemaOperation::class.java.declaredMethods.isEmpty())
        assertTrue(SchemaOperation::class.java.declaredFields.isEmpty())
    }
}
