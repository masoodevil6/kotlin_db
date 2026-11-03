package gog.my_project.data_base_schema

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.params.data_types.VarcharType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaOperationAdapterTest {
    @Test
    fun `adapter retains exactly one schema operation and its AST`() {
        val builder = SchemaAddColumnBuilder()
        builder.tableName("customers")
        builder.name("contact_name")
        builder.dataType(VarcharType(120))
        val originalAst = builder.ast

        val operation = adaptSchemaOperation(builder)
        val adapter = assertIs<ExistingSchemaOperationAdapter>(operation)

        assertSame(builder, adapter.schemaApi)
        assertSame(originalAst, operation.requireSchemaApi().ast)
    }

    @Test
    fun `adapter rejects a null schema operation`() {
        assertFailsWith<IllegalArgumentException> {
            adaptSchemaOperation(null)
        }
    }

    @Test
    fun `public operation contract is sealed and exposes no AST or parameter members`() {
        val contract = SchemaOperation::class.java

        assertTrue(contract.isSealed)
        assertTrue(contract.declaredMethods.isEmpty())
        assertTrue(contract.declaredFields.isEmpty())
        assertFalse(contract.methods.any { it.name == "getAst" || it.name == "getParams" })
        assertFalse(ISchemaApi::class.java.methods.any { it.name == "getParams" })
    }
}
