package gog.my_project.data_base.migration.example.v1.migrations.drop_index

import gog.my_project.data_base_schema.*





class A1ExampleDropIndexV1(
    private val table: String = "users",
    private val index: String = "idx_users_email",
) {
    fun migration(): SchemaOperation =
        Schema.dropIndex {
            tableName(table)
            name(index)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)
        println("\n=============================================")
        println("DROP INDEX example")
        println("---------------------------")
        println(sql)
    }

    }
