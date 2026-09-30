package gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index

import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.builder.createFullTextIndex
import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor

class A1ExampleCreateFullTextIndexV1(
    private val table: String = "posts",
    private val index: String = "ft_posts_title_body",
    private val columns: List<String> = listOf("title", "body"),
) {
    fun migration(): IMigrationCreateFullTextIndexApi =
        createFullTextIndex {
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
