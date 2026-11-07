package gog.my_project.data_base_migration.api.builder

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationDefinitionBuilderTest {
    @Test
    fun genericEntryPointKeepsTheFacadeOperationReference() {
        val schemaOperation = Schema.dropTable { tableName("only") }

        val definition = migration { operation(schemaOperation) }

        assertSame(schemaOperation, definition.operation)
    }

    @Test
    fun differentSchemaOperationsUseTheSameGenericEntryPoint() {
        val operations = listOf(
            Schema.dropTable { tableName("users") },
            Schema.createIndex {
                tableName("users")
                name("idx_users_email")
                column("email")
            },
            Schema.renameColumn {
                tableName("users")
                name("display_name")
                to("full_name")
            },
        )

        val definitions = operations.map { supplied -> migration { operation(supplied) } }

        assertEquals(operations.size, definitions.size)
        definitions.zip(operations).forEach { (definition, supplied) ->
            assertSame(supplied, definition.operation)
        }
    }

    @Test
    fun emptyDefinitionIsRejected() {
        val failure = assertFailsWith<IllegalStateException> { migration { } }
        assertEquals("MigrationDefinitionBuilder requires one top-level Schema operation", failure.message)
    }

    @Test
    fun secondOperationRegistrationIsRejectedWithoutReplacingTheFirst() {
        val first = Schema.dropTable { tableName("users") }
        val second = Schema.dropTable { tableName("users_archive") }
        val builder = MigrationDefinitionBuilder()

        builder.operation(first)
        val failure = assertFailsWith<IllegalStateException> { builder.operation(second) }

        assertEquals(
            "MigrationDefinitionBuilder accepts exactly one top-level Schema operation",
            failure.message,
        )
        assertSame(first, builder.build().operation)
    }

    @Test
    fun nestedCreateTableDefinitionsRemainOneTopLevelOperation() {
        val schemaOperation = Schema.createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(SchemaDataType.IntType)
                notNull()
            }
            addColumn {
                name("email")
                dataType(SchemaDataType.VarcharType(255))
                notNull()
            }
        }

        val definition = migration { operation(schemaOperation) }
        val renderedSql = Schema.render(schemaOperation, SchemaDialect.MYSQL)

        assertSame(schemaOperation, definition.operation)
        assertTrue(renderedSql.contains("CREATE TABLE `customers`"))
        assertTrue(renderedSql.contains("`email` VARCHAR(255) NOT NULL"))
    }
}
