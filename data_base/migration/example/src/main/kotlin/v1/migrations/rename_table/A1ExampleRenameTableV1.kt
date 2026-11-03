package gog.my_project.data_base.migration.example.v1.migrations.rename_table

import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.builder.ast.rename_table.SchemaRenameTableBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor




class A1ExampleRenameTableV1(
    private val from: String = "users",
    private val to: String = "customers",
) {

    fun migration(): ISchemaRenameTableApi =
        SchemaRenameTableBuilder().apply {
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