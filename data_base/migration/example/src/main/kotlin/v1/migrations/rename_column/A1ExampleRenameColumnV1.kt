package gog.my_project.data_base.migration.example.v1.migrations.rename_column

import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.builder.renameColumn
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleRenameColumnV1(
    private val table: String = "users",
    private val from: String = "display_name",
    private val target: String = "full_name",
) {
    fun migration(): IMigrationRenameColumnApi = renameColumn {
        tableName(table)
        name(from)
        to(target)
    }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("RENAME COLUMN example")
        println("---------------------------")
        println(sql)
    }

    fun execute(executor: IMigrationExecutor) {
        executor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("rename column query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("rename column execute: $result") },
        )
    }
}
