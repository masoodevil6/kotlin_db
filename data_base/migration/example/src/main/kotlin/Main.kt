package gog.my_project.data_base.migration.example

import gog.my_project.data_base.migration.example.managers.ManagerExampleV1
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_schema.SchemaVersion

fun main() {
    val examples = ManagerExampleV1(statusRunCreateForeignKey = true)
    examples.readyListExamples()
    examples.renderExamples(
        dialect = DatabaseDialect.MARIA_DB,
        version = SchemaVersion(DatabaseDialect.MARIA_DB, 10, 4, 28),
    )
}
