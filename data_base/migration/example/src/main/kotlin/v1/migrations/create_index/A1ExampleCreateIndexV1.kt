package gog.my_project.data_base.migration.example.v1.migrations.create_index

import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.builder.createIndex
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleCreateIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_email",
    private val column: String = "email",
) {
    fun migration(): IMigrationCreateIndexApi =
        createIndex {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE INDEX example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create index execute: $result") },
        )
    }
}
