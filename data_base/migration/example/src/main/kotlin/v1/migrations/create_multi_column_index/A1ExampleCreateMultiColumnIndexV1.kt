package gog.my_project.data_base.migration.example.v1.migrations.create_multi_column_index

import gog.my_project.data_base_schema.*





class A1ExampleCreateMultiColumnIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_name_email",
    private val columns: List<String> = listOf("last_name", "first_name", "email"),
) {
    fun migration(): SchemaOperation =
        Schema.createMultiColumnIndex {
            tableName(table)
            name(index)
            columns(*columns.toTypedArray())
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE MULTI-COLUMN INDEX example")
        println("---------------------------")
        println(sql)
    }

    }
