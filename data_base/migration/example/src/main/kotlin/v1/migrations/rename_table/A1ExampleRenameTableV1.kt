package gog.my_project.data_base.migration.example.v1.migrations.rename_table

import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.builder.renameTable
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleRenameTableV1(
    private val from: String = "users",
    private val to: String = "customers",
) {

    fun migration(): IMigrationRenameTableApi =
        renameTable {
            fromTableName(from)
            toTableName(to)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast)
            ?: error("Migration renderer returned no SQL")

        println("\n=============================================")
        println("RENAME TABLE example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, paramsMap ->
                println("rename table query: $query")
                println("params: $paramsMap")
            },
            blockExecute = { result ->
                println("rename table execute: $result")
            },
        )
    }
}