package gog.my_project.data_base.migration.example.v1.migrations.drop_table

import gog.my_project.data_base_schema.*





class A1ExampleDropTableV1(
    private val useIfExists: Boolean = false,
) {

    fun migration(): SchemaOperation =
        Schema.dropTable {
            tableName("users")
            if (useIfExists) ifExists()
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)

        println("\n=============================================")
        println("DROP TABLE example (ifExists=$useIfExists)")
        println("---------------------------")
        println(sql)
    }

    }