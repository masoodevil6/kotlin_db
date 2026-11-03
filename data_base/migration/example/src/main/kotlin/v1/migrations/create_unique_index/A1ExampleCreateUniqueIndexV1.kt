package gog.my_project.data_base.migration.example.v1.migrations.create_unique_index

import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.builder.ast.create_unique_index.SchemaCreateUniqueIndexBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor




class A1ExampleCreateUniqueIndexV1(
    private val table: String = "users",
    private val index: String = "uq_users_email",
    private val column: String = "email",
) {
    fun migration(): ISchemaCreateUniqueIndexApi =
        SchemaCreateUniqueIndexBuilder().apply {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE UNIQUE INDEX example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create unique index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create unique index execute: $result") },
        )
    }
}
