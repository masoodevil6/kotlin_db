package gog.my_project.data_base.migration.example.v1.migrations.create_index

import gog.my_project.data_base_schema.*





class A1ExampleCreateIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_email",
    private val column: String = "email",
) {
    fun migration(): SchemaOperation =
        Schema.createIndex {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE INDEX example")
        println("---------------------------")
        println(sql)
    }

    }
