package gog.my_project.data_base.migration.example.v1.migrations.add_column

import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.builder.addColumn
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.params.data_types.VarcharType

class A1ExampleAddColumnV1(
    private val table: String = "users",
    private val column: String = "email",
) {
    fun migration(): IMigrationAddColumnApi =
        addColumn {
            tableName(table)
            name(column)
            dataType(VarcharType(255))
            notNull()
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast)
            ?: error("Migration renderer returned no SQL")

        println("\n=============================================")
        println("ADD COLUMN example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, paramsMap ->
                println("add column query: $query")
                println("params: $paramsMap")
            },
            blockExecute = { result ->
                println("add column execute: $result")
            },
        )
    }
}