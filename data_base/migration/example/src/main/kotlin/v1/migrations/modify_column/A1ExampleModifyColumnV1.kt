package gog.my_project.data_base.migration.example.v1.migrations.modify_column

import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.builder.modifyColumn
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.params.data_types.VarcharType

class A1ExampleModifyColumnV1(
    private val table: String = "users",
    private val column: String = "name",
) {
    fun migration(): IMigrationModifyColumnApi =
        modifyColumn {
            tableName(table)
            name(column)
            dataType(VarcharType(255))
            notNull()
            default("Unknown")
            autoIncrement(false)
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("MODIFY COLUMN example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("modify column query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("modify column execute: $result") },
        )
    }
}
