package gog.my_project.data_base.migration.example.v1.migrations.rename_table

import gog.my_project.data_base_schema.*





class A1ExampleRenameTableV1(
    private val from: String = "users",
    private val to: String = "customers",
) {

    fun migration(): SchemaOperation =
        Schema.renameTable {
            fromTableName(from)
            toTableName(to)
        }

    fun render(dialect: SchemaDialect, version: SchemaVersion? = null) {
        val sql = Schema.render(migration(), dialect, version)

        println("\n=============================================")
        println("RENAME TABLE example")
        println("---------------------------")
        println(sql)
    }

    }