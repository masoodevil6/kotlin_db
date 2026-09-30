package gog.my_project.data_base.migration.api.interfaces

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MigrationDefinitionTest {
    @Test
    fun constructorSnapshotsMembershipAndOrderButKeepsOperationReferences() {
        val first = TestMigrationApi(TestAst("first"))
        val second = TestMigrationApi(TestAst("second"))
        val source = mutableListOf<IMigrationApi<*>>(first, second)

        val definition = MigrationDefinition(source)
        source.clear()

        assertEquals(2, definition.operations.size)
        assertSame(first, definition.operations[0])
        assertSame(second, definition.operations[1])

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

    private data class TestAst(val value: String) : IMigrationAst

    private class TestMigrationApi(
        override var ast: TestAst,
        override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    ) : IMigrationApi<TestAst>
}
