package gog.my_project.data_base.migration.example.v1.migrations.create_multi_column_index

import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.builder.ast.create_multi_column_index.SchemaCreateMultiColumnIndexBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor




class A1ExampleCreateMultiColumnIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_name_email",
    private val columns: List<String> = listOf("last_name", "first_name", "email"),
) {
    fun migration(): ISchemaCreateMultiColumnIndexApi =
        SchemaCreateMultiColumnIndexBuilder().apply {
            tableName(table)
            name(index)
            columns(*columns.toTypedArray())
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE MULTI-COLUMN INDEX example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create multi-column index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create multi-column index execute: $result") },
        )
    }
}
