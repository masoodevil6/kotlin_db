package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MigrationDefinitionTest {
    @Test
    fun constructorSnapshotsTheSingleOperationAndKeepsItsReference() {
        val first = TestMigrationApi(TestAst("first"))
        val source = mutableListOf<IMigrationApi<*>>(first)

        val definition = MigrationDefinition(source)
        source.clear()

        assertEquals(1, definition.operations.size)
        assertSame(first, definition.operations.single())

        val changedAst = TestAst("changed")
        first.ast = changedAst
        assertSame(changedAst, definition.operations[0].ast)
    }

    @Test
    fun exposedOperationsListCannotBeModified() {
        val definition = MigrationDefinition(listOf(TestMigrationApi(TestAst("only"))))

        assertFailsWith<UnsupportedOperationException> {
            (definition.operations as MutableList<IMigrationApi<*>>).clear()
        }
    }

    @Test
    fun publicConstructorRejectsEmptyAndMultipleOperations() {
        val first = TestMigrationApi(TestAst("first"))
        val second = TestMigrationApi(TestAst("second"))

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
        val nullOperation = listOf(null) as List<IMigrationApi<*>>
        val nullFailure = assertFailsWith<IllegalArgumentException> {
            MigrationDefinition(nullOperation)
        }
        assertEquals("MigrationDefinition must contain a non-null top-level operation", nullFailure.message)
    }

    @Test
    fun migrationUpAndDownBothRejectInvalidDirectDefinitions() {
        val operation = TestMigrationApi(TestAst("one"))
        val extraOperation = TestMigrationApi(TestAst("two"))
        val migration = object : Migration {
            override fun up() = MigrationDefinition(emptyList())
            override fun down() = MigrationDefinition(listOf(operation, extraOperation))
        }

        assertFailsWith<IllegalArgumentException> { migration.up() }
        assertFailsWith<IllegalArgumentException> { migration.down() }
    }

    private data class TestAst(val value: String) : IMigrationAst<TestAstMetadata> {
        override var metadata = TestAstMetadata()
    }

    private class TestAstMetadata : gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

    private class TestMigrationApi(
        override var ast: TestAst,
        override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    ) : IMigrationApi<TestAst>
}
