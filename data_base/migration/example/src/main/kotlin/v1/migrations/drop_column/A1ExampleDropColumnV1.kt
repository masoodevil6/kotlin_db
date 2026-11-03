package gog.my_project.data_base.migration.example.v1.migrations.drop_column

import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor




class A1ExampleDropColumnV1(
    private val table: String = "users",
    private val column: String = "display_name",
    private val useIfExists: Boolean = false,
) {
    fun migration(): ISchemaDropColumnApi =
        SchemaDropColumnBuilder().apply {
            tableName(table)
            name(column)
            if (useIfExists) ifExists()
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast)
            ?: error("Migration renderer returned no SQL")

        println("\n=============================================")
        println("DROP COLUMN example (ifExists=$useIfExists)")
        println("---------------------------")
        if (useIfExists) {
            println("Executor preflight query:")
            println(
                "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :tableName " +
                    "AND COLUMN_NAME = :columnName LIMIT 1",
            )
            println("params: {tableName=$table, columnName=$column}")
        }
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, paramsMap ->
                println("drop column query: $query")
                println("params: $paramsMap")
            },
            blockExecute = { result ->
                println("drop column execute: $result")
            },
        )
    }
}
