package gog.my_project.data_base.migration.example.v1.migrations.create_unique_index

import gog.my_project.data_base_schema.*





class A1ExampleCreateUniqueIndexV1(
    private val table: String = "users",
    private val index: String = "uq_users_email",
    private val column: String = "email",
) {
    fun migration(): SchemaOperation =
        Schema.createUniqueIndex {
            tableName(table)
            name(index)
            column(column)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("CREATE UNIQUE INDEX example")
        println("---------------------------")
        println(sql)
    }

    }
