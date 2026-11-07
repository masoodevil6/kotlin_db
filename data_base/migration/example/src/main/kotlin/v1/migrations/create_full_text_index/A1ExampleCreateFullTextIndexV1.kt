package gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index

import gog.my_project.data_base_schema.*





class A1ExampleCreateFullTextIndexV1(
    private val table: String = "posts",
    private val index: String = "ft_posts_title_body",
    private val columns: List<String> = listOf("title", "body"),
) {
    fun migration(): SchemaOperation =
        Schema.createFullTextIndex {
            tableName(table)
            name(index)
            columns(*this@A1ExampleCreateFullTextIndexV1.columns.toTypedArray())
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE FULLTEXT INDEX example")
        println("---------------------------")
        println(sql)
    }

    }
