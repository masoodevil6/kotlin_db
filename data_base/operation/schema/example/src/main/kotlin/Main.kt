package gog.my_project.data_base_schema.example

import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base_schema.example.operations.CustomerSchemaOperations
import gog.my_project.data_base_schema.renderer.manager.DialectSelector

/**
 * Renders direct Schema operation examples. This entry point does not open a database connection.
 */
fun main() = renderSchemaOperations()

private fun renderSchemaOperations() {
    val dialect = DialectSelector().select(DialectQuery.MARIA_DB)
    CustomerSchemaOperations.examples().forEach { example ->
        val sql = requireNotNull(dialect.render(example.operation.ast)) {
            "Schema renderer returned no SQL for ${example.name}"
        }
        println("${example.name}:\n$sql\n")
    }
}
