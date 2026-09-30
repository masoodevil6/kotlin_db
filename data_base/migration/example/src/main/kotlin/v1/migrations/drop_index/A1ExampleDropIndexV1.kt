package gog.my_project.data_base.migration.example.v1.migrations.drop_index

import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.builder.dropIndex
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleDropIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_email",
) {
    fun migration(): IMigrationDropIndexApi =
        dropIndex {
            tableName(table)
            name(index)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("DROP INDEX example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("drop index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("drop index execute: $result") },
        )
    }
}
