package gog.my_project.data_base.migration.example.v1.migrations.drop_column

import gog.my_project.data_base_schema.*





class A1ExampleDropColumnV1(
    private val table: String = "users",
    private val column: String = "display_name",
    private val useIfExists: Boolean = false,
) {
    fun migration(): SchemaOperation =
        Schema.dropColumn {
            tableName(table)
            name(column)
            if (useIfExists) ifExists()
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)

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

    }
