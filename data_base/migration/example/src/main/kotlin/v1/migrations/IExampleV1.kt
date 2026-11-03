package gog.my_project.data_base.migration.example.v1.migrations

import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

interface IExampleV1 {
    fun migration(): ISchemaRenderCreateTableApi

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast)
            ?: error("Migration renderer returned no SQL")

        println("\n=============================================")
        println("Migration V1")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, paramsMap ->
                println("query: $query")
                println("params: $paramsMap")
            },
            blockExecute = { result ->
                println("execute: $result")
            },
        )
    }
}
