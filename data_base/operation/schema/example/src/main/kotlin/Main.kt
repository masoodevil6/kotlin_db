package gog.my_project.data_base_schema.example

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDialect
import gog.my_project.data_base_schema.example.operations.CustomerSchemaOperations

/**
 * Renders direct Schema operation examples. This entry point does not open a database connection.
 */
fun main() = renderSchemaOperations()

private fun renderSchemaOperations() {
    CustomerSchemaOperations.examples().forEach { example ->
        val sql = Schema.render(example.operation, SchemaDialect.MARIA_DB)
        println("${example.name}:\n$sql\n")
    }
}
