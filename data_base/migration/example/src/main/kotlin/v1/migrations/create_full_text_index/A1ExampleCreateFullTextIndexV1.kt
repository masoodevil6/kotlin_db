package gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index

import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.builder.ast.create_full_text_index.SchemaCreateFullTextIndexBuilder
import gog.my_project.data_base_schema.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor




class A1ExampleCreateFullTextIndexV1(
    private val table: String = "posts",
    private val index: String = "ft_posts_title_body",
    private val columns: List<String> = listOf("title", "body"),
) {
    fun migration(): ISchemaCreateFullTextIndexApi =
        SchemaCreateFullTextIndexBuilder().apply {
            tableName(table)
            name(index)
            columns(*this@A1ExampleCreateFullTextIndexV1.columns.toTypedArray())
        }

    fun render(dialect: ISqlDialect) {
        val sql = dialect.render(migration().ast) ?: error("Migration renderer returned no SQL")
        println("\n=============================================")
        println("CREATE FULLTEXT INDEX example")
        println("---------------------------")
        println(sql)
    }

    fun execute(migrationExecutor: IMigrationExecutor) {
        migrationExecutor.execute(
            queryBuilder = migration(),
            blockQueryInfo = { query, params ->
                println("create fulltext index query: $query")
                println("params: $params")
            },
            blockExecute = { result -> println("create fulltext index execute: $result") },
        )
    }
}
