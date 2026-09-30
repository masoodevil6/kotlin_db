package gog.my_project.data_base.migration.example.v1.migrations.drop_table

import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.builder.dropTable
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleDropTableV1(
    private val useIfExists: Boolean = false,
) {

    fun migration(): IMigrationDropTableApi =
        dropTable {
            tableName("users")
            if (useIfExists) ifExists()
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast)
            ?: error("Migration renderer returned no SQL")

        println("\n=============================================")
        println("DROP TABLE example (ifExists=$useIfExists)")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, paramsMap ->
                println("drop table query: $query")
                println("params: $paramsMap")
            },
            blockExecute = { result ->
                println("drop table execute: $result")
            },
        )
    }
}