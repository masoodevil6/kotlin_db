package gog.my_project.data_base.migration.example.v1.migrations.add_column

import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base_schema.params.data_types.VarcharType




class A1ExampleAddColumnV1(
    private val table: String = "users",
    private val column: String = "email",
) {
    fun migration(): ISchemaAddColumnApi =
        SchemaAddColumnBuilder().apply {
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