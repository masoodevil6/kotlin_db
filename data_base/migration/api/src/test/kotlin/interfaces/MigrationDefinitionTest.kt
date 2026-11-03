package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.ast.schema.drop_table.SchemaDropTableAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MigrationDefinitionTest {
    @Test
    fun constructorSnapshotsTheSingleOperationAndKeepsItsReference() {
        val first = TestSchemaDropTableApi(SchemaDropTableAst().apply { metadata.tableName = "first" })
        val source = mutableListOf<ISchemaApi<*>>(first)

        val definition = MigrationDefinition(source)
        source.clear()

        assertEquals(1, definition.operations.size)
        assertSame(first, definition.operations.single())

        first.ast.metadata.tableName = "changed"
        assertSame(first.ast, definition.operations[0].ast)
        assertEquals("changed", (definition.operations[0] as ISchemaDropTableApi).ast.metadata.tableName)
    }

    @Test
    fun exposedOperationsListCannotBeModified() {
        val definition = MigrationDefinition(listOf(TestSchemaDropTableApi()))

        assertFailsWith<UnsupportedOperationException> {
            (definition.operations as MutableList<ISchemaApi<*>>).clear()
        }
    }

    @Test
    fun publicConstructorRejectsEmptyAndMultipleOperations() {
        val first = TestSchemaDropTableApi()
        val second = TestSchemaDropTableApi()

        val empty = assertFailsWith<IllegalArgumentException> {
            MigrationDefinition(emptyList())
        }
        assertEquals(
            "MigrationDefinition must contain exactly one top-level operation; found 0",
            empty.message,
        )

        val multiple = assertFailsWith<IllegalArgumentException> {
            MigrationDefinition(listOf(first, second))
        }
        assertEquals(
            "MigrationDefinition must contain exactly one top-level operation; found 2",
            multiple.message,
        )

        @Suppress("UNCHECKED_CAST")
        val nullOperation = listOf(null) as List<ISchemaApi<*>>
        val nullFailure = assertFailsWith<IllegalArgumentException> {
            MigrationDefinition(nullOperation)
        }
        assertEquals("MigrationDefinition must contain a non-null top-level operation", nullFailure.message)
    }

    @Test
    fun migrationUpAndDownBothRejectInvalidDirectDefinitions() {
        val operation = TestSchemaDropTableApi()
        val extraOperation = TestSchemaDropTableApi()
        val migration = object : Migration {
            override fun up() = MigrationDefinition(emptyList())
            override fun down() = MigrationDefinition(listOf(operation, extraOperation))
        }

        assertFailsWith<IllegalArgumentException> { migration.up() }
        assertFailsWith<IllegalArgumentException> { migration.down() }
    }

    private class TestSchemaDropTableApi(
        override var ast: ISchemaDropTableAst = SchemaDropTableAst(),
    ) : ISchemaDropTableApi {
        override fun tableName(table: String): ISchemaDropTableApi = apply { ast.metadata.tableName = table }

        override fun ifExists(): ISchemaDropTableApi = apply { ast.metadata.ifExists = true }
    }
}
